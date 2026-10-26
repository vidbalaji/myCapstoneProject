package org.example.apigateway_aug2026.filters;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.example.apigateway_aug2026.filters.RouteAccessPolicy.Access.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteAccessPolicyTest {

    private final RouteAccessPolicy policy = new RouteAccessPolicy();

    @Test
    void publicEndpoints() {
        assertEquals(PUBLIC, policy.requiredAccess(HttpMethod.POST, "/auth/login"));
        assertEquals(PUBLIC, policy.requiredAccess(HttpMethod.GET, "/products/5"));
        assertEquals(PUBLIC, policy.requiredAccess(HttpMethod.GET, "/categories/2/products"));
        assertEquals(PUBLIC, policy.requiredAccess(HttpMethod.GET, "/search"));
        assertEquals(PUBLIC, policy.requiredAccess(HttpMethod.POST, "/search"));
        assertEquals(PUBLIC, policy.requiredAccess(HttpMethod.POST, "/stripeWebhook"));
        assertEquals(PUBLIC, policy.requiredAccess(HttpMethod.POST, "/payment/webhook/razorpay"));
    }

    @Test
    void adminEndpoints() {
        assertEquals(ADMIN, policy.requiredAccess(HttpMethod.POST, "/products"));
        assertEquals(ADMIN, policy.requiredAccess(HttpMethod.PUT, "/products/5"));
        assertEquals(ADMIN, policy.requiredAccess(HttpMethod.DELETE, "/products/5"));
        assertEquals(ADMIN, policy.requiredAccess(HttpMethod.POST, "/categories"));
        assertEquals(ADMIN, policy.requiredAccess(HttpMethod.POST, "/search/reindex"));
        assertEquals(ADMIN, policy.requiredAccess(HttpMethod.PATCH, "/orders/12/status"));
        assertEquals(ADMIN, policy.requiredAccess(HttpMethod.GET, "/users/3"));
        assertEquals(ADMIN, policy.requiredAccess(HttpMethod.POST, "/payment"));
    }

    @Test
    void everythingElseNeedsALoggedInUser() {
        assertEquals(USER, policy.requiredAccess(HttpMethod.GET, "/cart"));
        assertEquals(USER, policy.requiredAccess(HttpMethod.POST, "/orders/checkout"));
        assertEquals(USER, policy.requiredAccess(HttpMethod.GET, "/orders/12/tracking"));
        assertEquals(USER, policy.requiredAccess(HttpMethod.GET, "/users/me"));
        assertEquals(USER, policy.requiredAccess(HttpMethod.GET, "/payment/order/12/receipt"));
        assertEquals(USER, policy.requiredAccess(HttpMethod.GET, "/some/new/endpoint"));
    }
}
