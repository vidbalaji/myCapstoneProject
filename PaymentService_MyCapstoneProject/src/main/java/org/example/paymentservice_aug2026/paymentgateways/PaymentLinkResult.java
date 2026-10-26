package org.example.paymentservice_aug2026.paymentgateways;

// url: what the customer opens to pay; reference: the gateway's id for the link (matched against webhooks)
public record PaymentLinkResult(String url, String reference) {
}
