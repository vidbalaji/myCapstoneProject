package org.example.apigateway_aug2026.filters;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

// Single place that says who may call what. Default is USER, so a new endpoint is never public by accident.
@Component
public class RouteAccessPolicy {

    public enum Access { PUBLIC, USER, ADMIN }

    public Access requiredAccess(HttpMethod method, String path) {
        // Login, signup, password reset
        if (path.startsWith("/auth/")) return Access.PUBLIC;

        // Gateway callbacks are verified by signature inside Payment Service; the mock page is for demos
        if (path.startsWith("/payment/mock/") || path.startsWith("/payment/webhook/") || path.equals("/stripeWebhook")) {
            return Access.PUBLIC;
        }
        // Raw "create a payment link for any amount" API: back-office only
        if (path.equals("/payment") && HttpMethod.POST.equals(method)) return Access.ADMIN;

        if (path.startsWith("/search")) {
            return path.startsWith("/search/reindex") ? Access.ADMIN : Access.PUBLIC;
        }
        // Catalog: anyone can browse, only admins can change it
        if (path.startsWith("/products") || path.startsWith("/categories")) {
            return HttpMethod.GET.equals(method) ? Access.PUBLIC : Access.ADMIN;
        }
        // Fulfilment status updates
        if (path.matches("^/orders/\\d+/status$") && HttpMethod.PATCH.equals(method)) return Access.ADMIN;

        // Looking up another user by id is for services/admins; users use /users/me
        if (path.matches("^/users/\\d+$")) return Access.ADMIN;

        return Access.USER;
    }
}
