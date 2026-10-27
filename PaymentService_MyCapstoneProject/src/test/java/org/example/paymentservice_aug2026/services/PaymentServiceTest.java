package org.example.paymentservice_aug2026.services;

import org.example.paymentservice_aug2026.clients.KafkaProducerClient;
import org.example.paymentservice_aug2026.dtos.OrderEventDto;
import org.example.paymentservice_aug2026.models.Payment;
import org.example.paymentservice_aug2026.models.PaymentGatewayType;
import org.example.paymentservice_aug2026.models.PaymentStatus;
import org.example.paymentservice_aug2026.repos.PaymentRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "razorpay.webhook.secret=test-webhook-secret")
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaProducerClient kafkaProducerClient;

    private OrderEventDto orderPlaced(long orderId, double total) {
        OrderEventDto event = new OrderEventDto();
        event.setEventType("ORDER_PLACED");
        event.setOrderId(orderId);
        event.setUserId(42L);
        event.setUserEmail("buyer@test.local");
        event.setTotalAmount(total);
        return event;
    }

    @Test
    void orderPlaced_CreatesPendingPaymentInPaise_AndIsIdempotent() {
        Payment first = paymentService.createPaymentForOrder(orderPlaced(101L, 1499.99));
        Payment again = paymentService.createPaymentForOrder(orderPlaced(101L, 1499.99));

        assertEquals(first.getId(), again.getId());
        assertEquals(149999L, first.getAmount());
        assertEquals(PaymentStatus.PENDING, first.getStatus());
        assertEquals(PaymentGatewayType.MOCK, first.getGateway());
        assertTrue(first.getPaymentLink().endsWith("/payment/mock/101"));
        verify(kafkaProducerClient, times(1)).sendMessage(eq(PaymentService.PAYMENT_EVENTS_TOPIC), eq("101"),
                argThat(m -> m.contains("PAYMENT_LINK_CREATED")));
    }

    @Test
    void markSucceeded_IssuesReceipt_PublishesOnce() {
        Payment payment = paymentService.createPaymentForOrder(orderPlaced(102L, 10.0));
        Payment paid = paymentService.markSucceeded(payment, "pay_1");
        paymentService.markSucceeded(paid, "pay_1");   // webhook retry

        assertEquals(PaymentStatus.SUCCEEDED, paid.getStatus());
        assertNotNull(paid.getReceiptNumber());
        verify(kafkaProducerClient, times(1)).sendMessage(anyString(), anyString(), argThat(m -> m.contains("PAYMENT_SUCCEEDED")));
    }

    @Test
    void orderCancelledAfterPayment_FlagsRefund() {
        Payment payment = paymentService.createPaymentForOrder(orderPlaced(103L, 10.0));
        paymentService.markSucceeded(payment, "pay_2");
        paymentService.handleOrderCancelled(103L);
        assertEquals(PaymentStatus.REFUND_PENDING, paymentRepo.findByOrderId(103L).orElseThrow().getStatus());
    }

    @Test
    void stripeWebhook_WithoutConfiguredSecret_IsRejected() throws Exception {
        mockMvc.perform(post("/stripeWebhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void razorpayWebhook_ValidSignature_MarksPaid_ForgedSignature_Rejected() throws Exception {
        Payment payment = paymentService.createPaymentForOrder(orderPlaced(104L, 25.0));
        String payload = """
                {"event":"payment_link.paid","payload":{
                  "payment_link":{"entity":{"id":"%s"}},
                  "payment":{"entity":{"id":"pay_rzp_1"}}}}
                """.formatted(payment.getGatewayReference());

        mockMvc.perform(post("/payment/webhook/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "forged").content(payload))
                .andExpect(status().isBadRequest());
        assertEquals(PaymentStatus.PENDING, paymentRepo.findByOrderId(104L).orElseThrow().getStatus());

        mockMvc.perform(post("/payment/webhook/razorpay").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", hmacSha256(payload, "test-webhook-secret")).content(payload))
                .andExpect(status().isOk());
        Payment paid = paymentRepo.findByOrderId(104L).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, paid.getStatus());
        assertEquals("pay_rzp_1", paid.getGatewayPaymentId());
    }

    private static String hmacSha256(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
