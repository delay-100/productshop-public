package com.whitedelay.productshop.cart.service;

import com.whitedelay.productshop.cart.dto.CartAllInfoResponseDto;
import com.whitedelay.productshop.cart.dto.CartInfoResponseDto;
import com.whitedelay.productshop.cart.dto.CartRequestDto;
import com.whitedelay.productshop.cart.entity.Cart;
import com.whitedelay.productshop.cart.repository.CartRepository;
import com.whitedelay.productshop.member.entity.Member;
import com.whitedelay.productshop.product.entity.Product;
import com.whitedelay.productshop.product.entity.ProductOption;
import com.whitedelay.productshop.product.repository.ProductOptionRepository;
import com.whitedelay.productshop.product.repository.ProductRepository;
import com.whitedelay.productshop.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;

    @Transactional
    public ApiResponse<CartInfoResponseDto> createCart(Member member, long productId, Long productOptionId, int quantity) {
        // 상품 조회
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("찾는 상품이 없습니다."));

        ProductOption productOption = null;
        if (productOptionId != null && productOptionId > 0) {
            // 상품 옵션 조회
            productOption = productOptionRepository.findByProductOptionId(productOptionId)
                    .orElseThrow(() -> new IllegalArgumentException("찾는 상품 옵션이 없습니다."));
        }

        // 카트에 있는 상품 조회
        Optional<Cart> optionalCart = cartRepository.findByMemberMemberIdAndProductProductIdAndCartProductOptionId(
                member.getMemberId(), productId, productOptionId != null ? productOptionId : 0L);

        Cart cart;
        if (optionalCart.isEmpty()) {
            // 카트에 없는 상품이라면 새로 생성
            CartRequestDto cartRequestDto = CartRequestDto.builder()
                    .cartProductOptionId(productOptionId != null ? productOptionId : 0L)
                    .cartProductStock(quantity)
                    .member(member)
                    .product(product)
                    .build();
            cart = Cart.from(cartRequestDto);
        } else {
            // 카트에 있는 상품이라면 수량을 증가시킴
            cart = optionalCart.get();
            cart.setCartProductStock(cart.getCartProductStock() + quantity);
        }
        cartRepository.save(cart);

        CartInfoResponseDto.CartInfoResponseDtoBuilder responseBuilder = CartInfoResponseDto.builder()
                .productId(productId)
                .productTitle(product.getProductTitle())
                .quantity(cart.getCartProductStock());

        if (productOption != null) {
            responseBuilder.productOptionId(productOptionId)
                    .productOptionName(productOption.getProductOptionName())
                    .productOptionPrice(productOption.getProductOptionPrice())
                    .productOptionStock(productOption.getProductOptionStock());
        }

        return ApiResponse.createSuccess(responseBuilder.build());
    }

    @Transactional
    public ApiResponse<Boolean> deleteCart(Member member, long productId, Long productOptionId) {
            // 카트에 있는 상품 조회
            Cart cart = cartRepository.findByMemberMemberIdAndProductProductIdAndCartProductOptionId(
                member.getMemberId(), productId, Objects.requireNonNullElse(productOptionId, 0L))
                    .orElseThrow(() -> new IllegalArgumentException("삭제할 항목이 없습니다."));

            cartRepository.delete(cart);

            return ApiResponse.createSuccess(true);

    }

    @Transactional(readOnly = true)
    public ApiResponse<CartAllInfoResponseDto> getCartAllInfo(Member member) {
        List<Cart> cartList = cartRepository.findByMemberMemberId(member.getMemberId());

        List<CartInfoResponseDto> cartInfoResponseDtoList = cartList.stream().map(cart -> {
            Product product = cart.getProduct();
            ProductOption productOption = cart.getCartProductOptionId() != 0 ?
                    product.getProductOptions().stream().filter(option -> option.getProductOptionId().equals(cart.getCartProductOptionId())).findFirst().orElse(null) : null;

            return CartInfoResponseDto.builder()
                    .productId(product.getProductId())
                    .productTitle(product.getProductTitle())
                    .quantity(cart.getCartProductStock())
                    .productOptionId(productOption != null ? productOption.getProductOptionId() : 0)
                    .productOptionName(productOption != null ? productOption.getProductOptionName() : null)
                    .productOptionPrice(productOption != null ? productOption.getProductOptionPrice() : 0)
                    .productOptionStock(productOption != null ? productOption.getProductOptionStock() : 0)
                    .build();
        }).collect(Collectors.toList());

        int totalPrice = cartInfoResponseDtoList.stream()
                .mapToInt(dto -> dto.getProductOptionPrice() * dto.getQuantity())
                .sum();

        return ApiResponse.createSuccess(CartAllInfoResponseDto.from(cartInfoResponseDtoList, totalPrice));
    }
}