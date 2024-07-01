package com.whitedelay.productshop.member.service;

import com.whitedelay.productshop.member.dto.*;
import com.whitedelay.productshop.member.entity.Member;
import com.whitedelay.productshop.order.entity.Order;
import com.whitedelay.productshop.order.entity.OrderProduct;
import com.whitedelay.productshop.order.entity.OrderStatusEnum;
import com.whitedelay.productshop.order.repository.OrderProductRepository;
import com.whitedelay.productshop.order.repository.OrderRepository;
import com.whitedelay.productshop.product.entity.Product;
import com.whitedelay.productshop.product.entity.ProductOption;
import com.whitedelay.productshop.product.repository.ProductOptionRepository;
import com.whitedelay.productshop.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final OrderRepository orderRepository;
    private final OrderProductRepository orderProductRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductRepository productRepository;

    /**
     * 멤버의 주문 목록을 페이지네이션하여 조회하는 메서드
     *
     * @param member 멤버 객체
     * @param page     페이지 번호 (0부터 시작)
     * @param size     페이지 당 주문 수
     * @return 주문 목록 페이지
     */
    @Transactional(readOnly = true)
    public Page<OrderListResponseDto> getOrderList(Member member, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderRepository.findByMemberMemberId(member.getMemberId(), pageable);

        return orders.map(order -> {
            List<OrderProduct> orderProducts = orderProductRepository.findByOrderOrderId(order.getOrderId());
            String productTitle = orderProducts.stream()
                    .map(orderProduct -> productRepository.findByProductId(orderProduct.getProduct().getProductId())
                            .map(Product::getProductTitle)
                            .orElse("Unknown Product"))
                    .findFirst()
                    .orElse("Unknown Product");
            int orderProductCount = orderProducts.size(); // 총 orderProduct 수

            return OrderListResponseDto.from(order, productTitle, orderProductCount);
        });
    }

    /**
     * 멤버의 주문 상세 정보를 조회하는 메서드
     *
     * @param member 멤버 객체
     * @param orderId 주문 ID
     * @return 주문 상세 정보
     * @throws IllegalArgumentException 주문이 존재하지 않을 경우 예외 발생
     */
    @Transactional(readOnly = true)
    public OrderDetailResponseDto getOrderDetail(Member member, long orderId) {
        Order order = orderRepository.findByMemberMemberIdAndOrderId(member.getMemberId(), orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for memberId: " + member.getMemberId() + " and orderId: " + orderId);
        }

        List<OrderProduct> orderProducts = orderProductRepository.findByOrderOrderId(orderId);
        if (orderProducts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No OrderProducts found for orderId: " + orderId);
        }

        List<OrderProductDetailResponseDto> orderProductDetailResponseDto = orderProducts.stream()
                .map(orderProduct -> {
                    Product product = productRepository.findByProductId(orderProduct.getProduct().getProductId()).orElseThrow(() -> new IllegalArgumentException("찾는 상품이 없습니다."));

                    Optional<ProductOption> productOption = productOptionRepository.findByProductOptionId(orderProduct.getOrderProductOptionId());
                    String productOptionName = productOption.map(ProductOption::getProductOptionName).orElse("Unknown Option");
                    return OrderProductDetailResponseDto.from(orderProduct, product.getProductTitle(), productOptionName);
                }).collect(Collectors.toList());

        return OrderDetailResponseDto.from(order, orderProductDetailResponseDto);
    }


    /**
     * 멤버의 주문 상태를 취소로 업데이트하는 메서드
     *
     * @param member 멤버 객체
     * @param orderId 주문 ID
     * @return 주문 취소 응답 DTO
     */
    @Transactional
    public OrderCancelResponseDto updateOrderStatusCancel(Member member, long orderId) {
        Order order = orderRepository.findByMemberMemberIdAndOrderId(member.getMemberId(), orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for memberId: " + member.getMemberId() + " and orderId: " + orderId);
        }

        // 취소 가능 상태인지 확인
        if (!order.getOrderStatus().isCancellable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel order after it has been shipped.");
        }

        order.setOrderStatus(OrderStatusEnum.ORDER_CANCELLED);

        List<OrderProduct> orderProducts = orderProductRepository.findByOrderOrderId(orderId);
        for (OrderProduct orderProduct : orderProducts) {
            Product product = orderProduct.getProduct();
            product.setProductStock(product.getProductStock() + orderProduct.getOrderProductQuantity());
            productRepository.save(product);
        }

        orderRepository.save(order);

        return OrderCancelResponseDto.from(order);
    }


    /**
     * 멤버의 주문 상태를 취소로 업데이트하는 메서드
     *
     * @param member 멤버 객체
     * @param orderId 주문 ID
     * @return 주문 취소 응답 DTO
     */
    @Transactional
    public OrderReturnResponseDto updateOrderStatusReturn(Member member, long orderId) {
        Order order = orderRepository.findByMemberMemberIdAndOrderId(member.getMemberId(), orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for memberId: " + member.getMemberId() + " and orderId: " + orderId);
        }

        // 반품 가능 상태인지 확인
        if (!order.getOrderStatus().isReturnable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only delivered orders can be returned.");
        }

        // 반품 가능 기간인지 확인 (배송 완료 후 1일 이내)
        if (order.getUpdatedAt().plusMinutes(1).isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return period has expired.");
        }

        order.setOrderStatus(OrderStatusEnum.RETURN_REQUESTED);
        orderRepository.save(order);

        return OrderReturnResponseDto.from(order);
    }
}

