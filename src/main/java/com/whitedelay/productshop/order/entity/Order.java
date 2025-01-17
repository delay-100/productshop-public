package com.whitedelay.productshop.order.entity;

import com.whitedelay.productshop.member.entity.Member;
import com.whitedelay.productshop.order.dto.OrderRequestDto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Builder
@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "`order`")
public class Order extends Timestamped {

    // Order시 처음에 백엔드에서 넣어줘야 하는 값
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Column(nullable = false)
    private LocalDateTime orderDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatusEnum orderStatus;

    // 백 -> 프 -> 백
    @Column(nullable = false)
    private int orderShippingFee;

    @Column(nullable = false)
    private int orderPrice;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderCardCompanyEnum orderCardCompany;

    // 결제 완료 시 사용할 주소 정보
    @Column(nullable = false)
    private String orderMemberName;

    @Column(nullable = false)
    private int orderZipCode;

    @Column(nullable = false)
    private String orderAddress;

    @Column(nullable = false)
    private String orderPhone;

    @Column(nullable = false)
    private String orderReq;

    // 결제한 아이디
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="member_id", nullable = false)
    private Member member;

    public static Order from(OrderRequestDto order) {
        return Order.builder()
                .orderDate(order.getOrderDate())
                .orderStatus(order.getOrderStatus())
                .orderShippingFee(order.getOrderShippingFee())
                .orderPrice(order.getOrderPrice())
                .orderCardCompany(order.getOrderCardCompany())
                .orderMemberName(order.getOrderMemberName())
                .orderZipCode(order.getOrderZipCode())
                .orderAddress(order.getOrderAddress())
                .orderPhone(order.getOrderPhone())
                .orderReq(order.getOrderReq())
                .member(order.getMember())
                .build();
    }

    public void setOrderStatus(OrderStatusEnum orderStatusEnum) {
        this.orderStatus = orderStatusEnum;
    }

}
