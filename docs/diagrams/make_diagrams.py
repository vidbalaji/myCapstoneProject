#!/usr/bin/env python3
"""Generates the architecture, UML, ER and deployment diagrams used in the README and project report.

    python3 docs/diagrams/make_diagrams.py      (needs Graphviz "dot")
"""
import html
import os
import subprocess

OUT = os.path.dirname(os.path.abspath(__file__))
FONT = "Liberation Sans"
BLUE, BLUE_BG = "#1c5cab", "#e8f1fc"
ORANGE, ORANGE_BG = "#b4471d", "#fdeee7"
GREEN, GREEN_BG = "#0f7a52", "#e6f6ef"
GREY, GREY_BG = "#52514e", "#f3f2ef"
VIOLET, VIOLET_BG = "#4a3aa7", "#efedfb"


def render(name, body, rankdir="LR", extra=""):
    dot = f"""digraph G {{
  graph [rankdir={rankdir}, fontname="{FONT}", fontsize=12, nodesep=0.35, ranksep=0.55, pad=0.2, bgcolor=white {extra}];
  node  [fontname="{FONT}", fontsize=11, shape=box, style="rounded,filled", fillcolor="{GREY_BG}", color="{GREY}", penwidth=1.2];
  edge  [fontname="{FONT}", fontsize=9, color="{GREY}", arrowsize=0.7];
{body}
}}"""
    path = os.path.join(OUT, name)
    with open(path + ".dot", "w") as f:
        f.write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=200", path + ".dot", "-o", path + ".png"], check=True)
    os.remove(path + ".dot")


def uml(node_id, name, fields=(), methods=(), stereo=None, color=BLUE, bg=BLUE_BG):
    """UML class box as a Graphviz HTML label."""
    e = html.escape
    head = (f'<FONT POINT-SIZE="9">&laquo;{e(stereo)}&raquo;</FONT><BR/>' if stereo else "") + f"<B>{e(name)}</B>"
    rows = f'<TR><TD BGCOLOR="{bg}" ALIGN="CENTER">{head}</TD></TR>'
    if fields:
        rows += '<TR><TD ALIGN="LEFT" BALIGN="LEFT">' + "<BR/>".join(e(x) for x in fields) + "</TD></TR>"
    if methods:
        rows += '<TR><TD ALIGN="LEFT" BALIGN="LEFT">' + "<BR/>".join(e(x) for x in methods) + "</TD></TR>"
    return (f'  {node_id} [shape=plain, style="", label=<<TABLE BORDER="1" CELLBORDER="1" CELLSPACING="0" '
            f'CELLPADDING="4" COLOR="{color}">{rows}</TABLE>>];\n')


def table(node_id, name, cols, color=BLUE, bg=BLUE_BG):
    """ER table: cols = [(name, type, key)] where key in {'PK','FK','UK',''}."""
    e = html.escape
    rows = f'<TR><TD COLSPAN="3" BGCOLOR="{bg}"><B>{e(name)}</B></TD></TR>'
    for c, t, k in cols:
        key = f"<B>{k}</B>" if k else ""
        port = "".join(ch if ch.isalnum() or ch == "_" else "_" for ch in c)
        rows += (f'<TR><TD ALIGN="LEFT" PORT="{port}">{e(c)}</TD><TD ALIGN="LEFT"><FONT COLOR="{GREY}">{e(t)}</FONT></TD>'
                 f'<TD>{key}</TD></TR>')
    return (f'  {node_id} [shape=plain, style="", label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" '
            f'CELLPADDING="3" COLOR="{color}">{rows}</TABLE>>];\n')


def architecture():
    b = f"""
  client [label="Web / Mobile client", fillcolor=white];
  elb [label="AWS Elastic Load Balancer"];
  gw [label="API Gateway (Spring Cloud Gateway) :8080\\nauthentication · USER/ADMIN roles · rate limiting", fillcolor="{ORANGE_BG}", color="{ORANGE}"];
  eureka [label="Service Discovery\\nEureka :8761", fillcolor="{VIOLET_BG}", color="{VIOLET}"];
  redis_gw [label="Redis\\ntoken cache · rate limits", shape=cylinder, fillcolor="{GREEN_BG}", color="{GREEN}"];
  node [fillcolor="{BLUE_BG}", color="{BLUE}"];
  us [label="User / Auth\\n:8081"]; ps [label="Product Catalog\\n:8082"]; cs [label="Cart\\n:8083"];
  os [label="Order\\n:8084"]; pay [label="Payment\\n:8085"];
  {{ rank=same; us; ps; cs; os; pay; }}
  node [shape=cylinder, fillcolor="{GREEN_BG}", color="{GREEN}"];
  mu [label="MySQL\\nusers"]; mp [label="MySQL\\nproducts"]; es [label="Elasticsearch\\nproduct index"];
  mg [label="MongoDB\\ncarts"]; rc [label="Redis\\ncart cache"]; mo [label="MySQL\\norders"]; mpay [label="MySQL\\npayments"];
  {{ rank=same; mu; mp; es; mg; rc; mo; mpay; }}
  kafka [label="Apache Kafka\\nsignup · password-reset · session-revoked · cart-events · order-events · payment-events", shape=box, style="rounded,filled", fillcolor="{VIOLET_BG}", color="{VIOLET}"];
  ns [label="Notification\\n:8086", shape=box, style="rounded,filled", fillcolor="{BLUE_BG}", color="{BLUE}"];
  pg [label="Stripe / Razorpay", shape=box, style="rounded,filled", fillcolor=white, color="{GREY}"];
  ses [label="SMTP / Amazon SES", shape=box, style="rounded,filled", fillcolor=white, color="{GREY}"];
  client -> elb -> gw; eureka -> gw [style=dotted, arrowhead=none]; gw -> redis_gw [style=dashed];
  {{ rank=same; gw; eureka; redis_gw; }}
  gw -> us; gw -> ps; gw -> cs; gw -> os; gw -> pay;
  us -> mu; ps -> mp; ps -> es; cs -> mg; cs -> rc; os -> mo; pay -> mpay;
  cs -> ps [style=dashed, label="REST"]; os -> cs [style=dashed, label="REST"]; pay -> pg [dir=both];
  edge [color="{VIOLET}"];
  mu -> kafka [style=invis]; mpay -> kafka [style=invis];
  us -> kafka; cs -> kafka; os -> kafka [dir=both]; pay -> kafka [dir=both];
  kafka -> ns; ns -> ses [color="{GREY}"];
"""
    render("architecture", b, rankdir="TB")


def kafka_flow():
    b = f"""
  node [fillcolor="{BLUE_BG}", color="{BLUE}"];
  us [label="User Service"]; cs [label="Cart Service"]; os [label="Order Service"]; pay [label="Payment Service"];
  ns [label="Notification Service"]; gw [label="API Gateway", fillcolor="{ORANGE_BG}", color="{ORANGE}"];
  analytics [label="Analytics /\\nrecommendations (future)", style="rounded,dashed", fillcolor=white];
  node [shape=cds, fillcolor="{VIOLET_BG}", color="{VIOLET}"];
  t_signup [label="signup"]; t_reset [label="password-reset"]; t_rev [label="session-revoked"];
  t_cart [label="cart-events"]; t_order [label="order-events"]; t_pay [label="payment-events"];
  us -> t_signup; us -> t_reset; us -> t_rev;
  cs -> t_cart; os -> t_order; pay -> t_pay;
  t_signup -> ns; t_reset -> ns; t_rev -> gw [label="evict cached token"];
  t_cart -> analytics [style=dashed];
  t_order -> pay [label="ORDER_PLACED → create payment link\\nORDER_CANCELLED → cancel / refund"];
  t_order -> ns [label="confirmation, status emails"];
  t_pay -> os [label="LINK_CREATED / SUCCEEDED / FAILED\\n→ update order"];
  t_pay -> ns [label="payment link, receipt"];
"""
    render("kafka-topics", b)


def use_cases():
    b = f"""
  node [shape=ellipse, fillcolor="{BLUE_BG}", color="{BLUE}"];
  subgraph cluster_sys {{ label="E-commerce backend"; style="rounded"; color="{GREY}";
    uc1 [label="Register / Login / Logout"]; uc2 [label="Manage profile"]; uc3 [label="Reset password"];
    uc4 [label="Browse by category"]; uc5 [label="View product details"]; uc6 [label="Search products"];
    uc7 [label="Manage cart"]; uc8 [label="Checkout"]; uc9 [label="Pay for order"];
    uc10 [label="View order history"]; uc11 [label="Track order"]; uc12 [label="Get receipt"];
    uc13 [label="Cancel order"];
    uc14 [label="Manage catalog\\n(products, categories)", fillcolor="{ORANGE_BG}", color="{ORANGE}"];
    uc15 [label="Update fulfilment status", fillcolor="{ORANGE_BG}", color="{ORANGE}"];
    uc16 [label="Rebuild search index", fillcolor="{ORANGE_BG}", color="{ORANGE}"];
    uc17 [label="Send notifications", fillcolor="{VIOLET_BG}", color="{VIOLET}"];
  }}
  node [shape=box, style="filled", fillcolor=white, color="{GREY}"];
  guest [label="Guest"]; cust [label="Customer"]; admin [label="Admin"]; pg [label="Payment gateway\\n(Stripe / Razorpay)"];
  mail [label="Email provider"];
  guest -> {{uc1 uc3 uc4 uc5 uc6}} [arrowhead=none];
  cust -> {{uc2 uc7 uc8 uc9 uc10 uc11 uc12 uc13}} [arrowhead=none];
  admin -> {{uc14 uc15 uc16}} [arrowhead=none];
  uc9 -> pg [arrowhead=none]; uc17 -> mail [arrowhead=none];
  uc8 -> uc9 [style=dashed, label="«include»"];
  uc9 -> uc17 [style=dashed, label="«include»"];
  cust -> guest [arrowhead=empty, label="is a"]; admin -> cust [arrowhead=empty, label="is a"];
"""
    render("use-cases", b)


def class_user():
    b = (uml("BaseModel", "BaseModel", ["- id: Long", "- createdAt: Date", "- lastUpdatedAt: Date", "- status: Status"], stereo="MappedSuperclass")
         + uml("User", "User", ["- name: String", "- email: String {unique}", "- password: String (BCrypt)", "- phoneNumber: String {unique}", "- roles: List<Role>"], stereo="Entity")
         + uml("Role", "Role", ["- value: String"], stereo="Entity")
         + uml("UserSession", "UserSession", ["- token: String (JWT)", "- user: User"], stereo="Entity")
         + uml("PRT", "PasswordResetToken", ["- token: String {unique}", "- user: User", "- expiresAt: Date"], stereo="Entity")
         + uml("IAuth", "IAuthService", [], ["+ signup(name, email, password, phone): User", "+ login(email, password): Pair<User,String>", "+ validateToken(token): Boolean", "+ getUserForToken(token): User", "+ logout(token)", "+ requestPasswordReset(email)", "+ resetPassword(token, newPassword)"], stereo="interface", color=ORANGE, bg=ORANGE_BG)
         + uml("Auth", "AuthService", ["- userRepo, roleRepo, sessionRepo", "- secretKey: SecretKey", "- kafkaProducerClient"], ["- publishRevoked(token)", "- getOrCreateRole(value)"], stereo="Service", color=ORANGE, bg=ORANGE_BG)
         + uml("AuthC", "AuthController", [], ["POST /auth/signup | login | logout", "POST /auth/validate | validateToken", "POST /auth/forgot-password | reset-password"], stereo="RestController", color=GREEN, bg=GREEN_BG)
         + uml("UserC", "UserController", [], ["GET /users/{id}", "GET | PATCH /users/me"], stereo="RestController", color=GREEN, bg=GREEN_BG)
         + uml("Kafka", "KafkaProducerClient", [], ["+ sendMessage(topic, message)"], stereo="Component", color=VIOLET, bg=VIOLET_BG)
         + f"""
  edge [arrowhead=empty]; User -> BaseModel; Role -> BaseModel; UserSession -> BaseModel; PRT -> BaseModel;
  edge [arrowhead=empty, style=dashed]; Auth -> IAuth;
  edge [arrowhead=vee, style=solid];
  User -> Role [label="m : m", arrowhead=open]; UserSession -> User [label="m : 1", arrowhead=open]; PRT -> User [label="m : 1", arrowhead=open];
  AuthC -> IAuth; UserC -> User [style=dashed, label="uses"]; Auth -> Kafka; Auth -> UserSession [style=dashed]; Auth -> PRT [style=dashed];
""")
    render("class-user-service", b, rankdir="BT")


def class_product():
    b = (uml("BaseModel", "BaseModel", ["- id: Long", "- createdAt, lastUpdatedAt: Date", "- state: State"], stereo="MappedSuperclass")
         + uml("Product", "Product", ["- name: String", "- description: String", "- imageUrl: String", "- price: Double", "- category: Category", "- isSaleEligible: Boolean"], stereo="Entity")
         + uml("Category", "Category", ["- name: String", "- description: String", "- products: List<Product> (LAZY)"], stereo="Entity")
         + uml("IPS", "IProductService", [], ["+ getProductDetailsById(id)", "+ createProduct(p) / replaceProduct(id, p)", "+ getAllProducts(page, size)", "+ getProductsByCategory(catId, page, size)", "+ deleteProduct(id)"], stereo="interface", color=ORANGE, bg=ORANGE_BG)
         + uml("SPS", "StorageProductService", ["- productRepo, categoryRepo", "- productSearchIndexer"], ["- resolveCategory(c)"], stereo="Service, Primary", color=ORANGE, bg=ORANGE_BG)
         + uml("FPS", "ProductService (FakeStore)", ["- restTemplateBuilder"], [], stereo="Service", color=ORANGE, bg=ORANGE_BG)
         + uml("SS", "SearchService", ["- elasticsearchOperations", "- elasticsearchEnabled: boolean"], ["+ searchProducts(q, size, page, sortBy)", "+ reindexAll(): long", "- searchInElasticsearch(..)", "- searchInDatabase(..)"], stereo="Service", color=ORANGE, bg=ORANGE_BG)
         + uml("IIdx", "IProductSearchIndexer", [], ["+ index(p) / indexAll(list) / remove(id)"], stereo="interface", color=VIOLET, bg=VIOLET_BG)
         + uml("EIdx", "ElasticsearchProductSearchIndexer", [], [], stereo="Component", color=VIOLET, bg=VIOLET_BG)
         + uml("NIdx", "NoOpProductSearchIndexer", [], [], stereo="Component", color=VIOLET, bg=VIOLET_BG)
         + uml("Doc", "ProductDocument", ["- id, name, description", "- price, imageUrl", "- categoryId, categoryName"], stereo="Document: products", color=VIOLET, bg=VIOLET_BG)
         + uml("PC", "ProductController / CategoryController / SearchController", [], ["/products, /categories, /search"], stereo="RestController", color=GREEN, bg=GREEN_BG)
         + """
  edge [arrowhead=empty]; Product -> BaseModel; Category -> BaseModel;
  edge [arrowhead=empty, style=dashed]; SPS -> IPS; FPS -> IPS; EIdx -> IIdx; NIdx -> IIdx;
  edge [arrowhead=open, style=solid];
  Product -> Category [label="m : 1"]; PC -> IPS; PC -> SS; SPS -> IIdx; SS -> IIdx; EIdx -> Doc [style=dashed, label="maps to"];
""")
    render("class-product-service", b, rankdir="BT")


def class_cart_order():
    b = (uml("Cart", "Cart", ["- userId: Long  (@Id)", "- items: List<CartItem>", "- updatedAt: Date"], stereo="Document: carts")
         + uml("CartItem", "CartItem", ["- productId: Long", "- productName: String", "- unitPrice: Double", "- quantity: Integer"], stereo="embedded")
         + uml("CS", "CartService", ["- cartRepo: CartRepo (Mongo)", "- productServiceClient"], ["@Cacheable getCart(userId)", "@CachePut addItem / updateItemQuantity / removeItem", "@CacheEvict clearCart(userId)"], stereo="Service", color=ORANGE, bg=ORANGE_BG)
         + uml("Order", "Order", ["- userId: Long", "- userEmail: String", "- status: OrderStatus", "- totalAmount: Double", "- paymentMethod: PaymentMethod", "- paymentLink: String", "- deliveryAddress: Address", "- items: List<OrderItem> @BatchSize", "- statusHistory: List<OrderStatusHistory>"], stereo="Entity, table orders")
         + uml("OrderItem", "OrderItem", ["- productId, productName", "- unitPrice: Double", "- quantity: Integer"], stereo="Entity")
         + uml("Hist", "OrderStatusHistory", ["- status: OrderStatus", "- note: String", "- changedAt: Date"], stereo="Entity")
         + uml("Address", "Address", ["- fullName, line1, line2", "- city, state, pincode", "- phoneNumber"], stereo="Embeddable")
         + uml("OSt", "OrderStatus", ["PAYMENT_PENDING, PAYMENT_FAILED, PAID,", "SHIPPED, OUT_FOR_DELIVERY,", "DELIVERED, CANCELLED"], ["+ allowedNextStatuses(): Set", "+ canMoveTo(next): boolean"], stereo="enum", color=VIOLET, bg=VIOLET_BG)
         + uml("OS", "OrderService", ["- orderRepo", "- cartServiceClient, productServiceClient", "- kafkaProducerClient"], ["+ checkout(userId, email, address, method)", "+ getOrderHistory(userId, page, size)", "+ updateStatus(id, status, note)", "+ cancelOrder(id, userId)", "+ handlePaymentEvent(event)", "- publishAfterCommit(type, order)"], stereo="Service", color=ORANGE, bg=ORANGE_BG)
         + """
  edge [arrowhead=diamond, dir=back]; Cart -> CartItem [label="1 : m"];
  Order -> OrderItem [label="1 : m"]; Order -> Hist [label="1 : m"]; Order -> Address [label="embeds"];
  edge [arrowhead=open, dir=forward]; CS -> Cart; OS -> Order; Order -> OSt; OS -> CS [style=dashed, label="REST (Eureka)"];
""")
    render("class-cart-order", b, rankdir="LR")


def class_payment():
    b = (uml("Payment", "Payment", ["- orderId: Long {unique}", "- userId, userEmail", "- amount: Long (paise)", "- currency: String", "- gateway: PaymentGatewayType", "- status: PaymentStatus", "- paymentLink, gatewayReference", "- gatewayPaymentId, receiptNumber", "- paidAt: Date"], stereo="Entity")
         + uml("IPG", "IPaymentGateway", [], ["+ getType(): PaymentGatewayType", "+ createPaymentLink(amount, currency, phone,", "      name, email, orderId): PaymentLinkResult"], stereo="interface (Strategy)", color=ORANGE, bg=ORANGE_BG)
         + uml("Stripe", "StripePaymentGateway", [], [], stereo="Component", color=ORANGE, bg=ORANGE_BG)
         + uml("Rzp", "RazorpayPaymentGateway", [], [], stereo="Component", color=ORANGE, bg=ORANGE_BG)
         + uml("Mock", "MockPaymentGateway", [], [], stereo="Component", color=ORANGE, bg=ORANGE_BG)
         + uml("Sel", "PaymentGatewaySelector", ["- gateways: Map<Type, IPaymentGateway>", "- activeGateway (payment.gateway)"], ["+ getActiveGateway()"], stereo="Component", color=ORANGE, bg=ORANGE_BG)
         + uml("PS", "PaymentService", ["- paymentRepo, kafkaProducerClient"], ["+ createPaymentForOrder(orderEvent)", "+ markSucceeded(payment, gatewayPaymentId)", "+ markFailed(payment, reason)", "+ handleOrderCancelled(orderId)"], stereo="Service", color=GREEN, bg=GREEN_BG)
         + uml("WH", "StripeWebhookController / RazorpayWebhookController", [], ["verify signature → markSucceeded / markFailed"], stereo="RestController", color=GREEN, bg=GREEN_BG)
         + uml("KC", "KafkaConsumerOrderClient", [], ["@KafkaListener(order-events)"], stereo="Component", color=VIOLET, bg=VIOLET_BG)
         + """
  edge [arrowhead=empty, style=dashed]; Stripe -> IPG; Rzp -> IPG; Mock -> IPG;
  edge [arrowhead=open, style=solid]; Sel -> IPG [label="1 : m"]; PS -> Sel; PS -> Payment; WH -> PS; KC -> PS;
""")
    render("class-payment-service", b, rankdir="BT")


def class_gateway_notification():
    b = (uml("AF", "AuthenticationFilter", ["- routeAccessPolicy", "- userServiceClient: WebClient (lb)", "- redisTemplate (token cache)", "- cacheTtl: Duration"], ["+ filter(exchange, chain)", "- resolveUser(token): Mono<UserDto>", "- validateWithUserService(token)", "- extractToken(request)"], stereo="GlobalFilter, order -100", color=ORANGE, bg=ORANGE_BG)
         + uml("RAP", "RouteAccessPolicy", [], ["+ requiredAccess(method, path): PUBLIC | USER | ADMIN"], stereo="Component", color=ORANGE, bg=ORANGE_BG)
         + uml("SRL", "SessionRevocationListener", [], ["@KafkaListener(session-revoked)", "evicts auth:token:<sha256>"], stereo="Component", color=VIOLET, bg=VIOLET_BG)
         + uml("KR", "clientIpKeyResolver", [], ["key for RedisRateLimiter"], stereo="Bean", color=ORANGE, bg=ORANGE_BG)
         + uml("NT", "NotificationTemplates", [], ["+ forOrderEvent(e): Message", "+ forPaymentEvent(e): Message"], stereo="utility", color=BLUE, bg=BLUE_BG)
         + uml("ES", "EmailSender", ["- emailEnabled, smtpHost/port", "- smtpUsername/Password (env)"], ["+ send(to, subject, body)", "+ recentNotifications()"], stereo="Component", color=BLUE, bg=BLUE_BG)
         + uml("KL", "KafkaConsumer*Client", [], ["@KafkaListener signup, password-reset", "@KafkaListener order-events, payment-events"], stereo="Component", color=VIOLET, bg=VIOLET_BG)
         + """
  edge [arrowhead=open]; AF -> RAP; SRL -> AF [style=dashed, label="same cache"]; KL -> NT; KL -> ES;
  subgraph cluster_gw { label="API Gateway"; style=rounded; color="#b4471d"; AF; RAP; SRL; KR; }
  subgraph cluster_ns { label="Notification Service"; style=rounded; color="#1c5cab"; NT; ES; KL; }
""")
    render("class-gateway-notification", b, rankdir="LR")


def state_machine():
    b = f"""
  node [shape=box, style="rounded,filled", fillcolor="{BLUE_BG}", color="{BLUE}"];
  start [shape=circle, label="", width=0.2, fillcolor="{GREY}", color="{GREY}"];
  PP [label="PAYMENT_PENDING"]; PF [label="PAYMENT_FAILED"]; PAID [label="PAID"]; SH [label="SHIPPED"];
  OFD [label="OUT_FOR_DELIVERY"]; DEL [label="DELIVERED", fillcolor="{GREEN_BG}", color="{GREEN}"];
  CAN [label="CANCELLED", fillcolor="{ORANGE_BG}", color="{ORANGE}"];
  start -> PP [label="checkout"];
  PP -> PAID [label="PAYMENT_SUCCEEDED"]; PP -> PF [label="PAYMENT_FAILED"]; PF -> PAID [label="retry succeeds"];
  PAID -> SH [label="admin"]; SH -> OFD [label="admin"]; OFD -> DEL [label="admin"]; SH -> DEL [label="admin"];
  PP -> CAN [label="customer"]; PF -> CAN [label="customer"]; PAID -> CAN [label="customer\\n(refund flagged)"];
"""
    render("order-state-machine", b)


def er_diagrams():
    u = (table("user", "user", [("id", "BIGINT", "PK"), ("name", "VARCHAR", ""), ("email", "VARCHAR", "UK"), ("password", "VARCHAR", ""), ("phone_number", "VARCHAR", "UK"), ("status", "TINYINT", ""), ("created_at", "DATETIME", ""), ("last_updated_at", "DATETIME", "")])
         + table("role", "role", [("id", "BIGINT", "PK"), ("value", "VARCHAR", ""), ("status", "TINYINT", ""), ("created_at", "DATETIME", "")])
         + table("user_roles", "user_roles", [("user_id", "BIGINT", "FK"), ("roles_id", "BIGINT", "FK")], color=GREY, bg=GREY_BG)
         + table("user_session", "user_session", [("id", "BIGINT", "PK"), ("token", "VARCHAR(1024)", ""), ("user_id", "BIGINT", "FK"), ("status", "TINYINT", ""), ("created_at", "DATETIME", "")])
         + table("prt", "password_reset_token", [("id", "BIGINT", "PK"), ("token", "VARCHAR", "UK"), ("user_id", "BIGINT", "FK"), ("expires_at", "DATETIME", ""), ("created_at", "DATETIME", "")])
         + """
  edge [arrowhead=none, arrowtail=crow, dir=both];
  user:id -> user_roles:user_id [arrowtail=none, arrowhead=crow]; role:id -> user_roles:roles_id [arrowtail=none, arrowhead=crow];
  user:id -> user_session:user_id [arrowtail=none, arrowhead=crow]; user:id -> prt:user_id [arrowtail=none, arrowhead=crow];
""")
    render("er-user-service", u)
    p = (table("category", "category", [("id", "BIGINT", "PK"), ("name", "VARCHAR", ""), ("description", "VARCHAR", ""), ("state", "TINYINT", ""), ("created_at", "DATETIME", ""), ("last_updated_at", "DATETIME", "")])
         + table("product", "product", [("id", "BIGINT", "PK"), ("name", "VARCHAR", ""), ("description", "VARCHAR(2000)", ""), ("image_url", "VARCHAR", ""), ("price", "DOUBLE", ""), ("category_id", "BIGINT", "FK"), ("is_sale_eligible", "BIT", ""), ("state", "TINYINT", ""), ("created_at", "DATETIME", ""), ("last_updated_at", "DATETIME", "")])
         + table("esdoc", "Elasticsearch index: products", [("id", "long", "_id"), ("name", "text (english)", ""), ("description", "text (english)", ""), ("categoryName", "text", ""), ("categoryId", "long", ""), ("price", "double", ""), ("imageUrl", "keyword", "")], color=VIOLET, bg=VIOLET_BG)
         + """
  category:id -> product:category_id [arrowhead=crow];
  product:id -> esdoc:id [style=dashed, label="indexed on write", arrowhead=open];
""")
    render("er-product-service", p)
    o = (table("orders", "orders", [("id", "BIGINT", "PK"), ("user_id", "BIGINT", "IDX"), ("created_at", "DATETIME", "IDX"), ("user_email", "VARCHAR", ""), ("status", "ENUM", ""), ("total_amount", "DOUBLE", ""), ("currency", "VARCHAR", ""), ("payment_method", "ENUM", ""), ("payment_link", "VARCHAR(1024)", ""), ("full_name … phone_number", "address columns", ""), ("last_updated_at", "DATETIME", "")])
         + table("order_item", "order_item", [("id", "BIGINT", "PK"), ("order_id", "BIGINT", "FK"), ("product_id", "BIGINT", ""), ("product_name", "VARCHAR", ""), ("unit_price", "DOUBLE", ""), ("quantity", "INT", ""), ("created_at", "DATETIME", "")])
         + table("hist", "order_status_history", [("id", "BIGINT", "PK"), ("order_id", "BIGINT", "FK"), ("status", "ENUM", ""), ("note", "VARCHAR", ""), ("changed_at", "DATETIME", "")])
         + """
  orders:id -> order_item:order_id [arrowhead=crow]; orders:id -> hist:order_id [arrowhead=crow];
""")
    render("er-order-service", o)
    pm = (table("payment", "payment", [("id", "BIGINT", "PK"), ("order_id", "BIGINT", "UK"), ("user_id", "BIGINT", ""), ("user_email", "VARCHAR", ""), ("amount", "BIGINT (paise)", ""), ("currency", "VARCHAR", ""), ("gateway", "ENUM", ""), ("status", "ENUM", ""), ("payment_link", "VARCHAR(1024)", ""), ("gateway_reference", "VARCHAR", "IDX"), ("gateway_payment_id", "VARCHAR", ""), ("receipt_number", "VARCHAR", ""), ("failure_reason", "VARCHAR", ""), ("created_at / paid_at", "DATETIME", "")])
          + table("cart", "MongoDB collection: carts", [("_id", "Long (userId)", "PK"), ("items[]", "array", ""), ("  .productId", "Long", ""), ("  .productName", "String", ""), ("  .imageUrl", "String", ""), ("  .unitPrice", "Double", ""), ("  .quantity", "Integer", ""), ("updatedAt", "Date", "")], color=GREEN, bg=GREEN_BG)
          + table("redis", "Redis keys", [("carts::<userId>", "JSON, TTL 30 min", ""), ("auth:token:<sha256>", "JSON user, TTL 60 s", ""), ("request_rate_limiter.*", "token bucket", "")], color=ORANGE, bg=ORANGE_BG))
    render("er-payment-cart", pm)


def deployment():
    b = f"""
  users [label="Customers / Admins", fillcolor=white];
  r53 [label="Route 53 + ACM (TLS)", fillcolor=white];
  subgraph cluster_vpc {{ label="VPC 10.0.0.0/16  (2 Availability Zones)"; style=rounded; color="{GREY}";
    subgraph cluster_pub {{ label="Public subnets"; style="rounded,filled"; fillcolor="#fafafa"; color="{GREY}";
      alb [label="Application Load Balancer\\nsg-alb: 443 from internet", fillcolor="{ORANGE_BG}", color="{ORANGE}"];
      nat [label="NAT gateway", fillcolor=white];
    }}
    subgraph cluster_app {{ label="Private app subnets: Elastic Beanstalk / EC2 Auto Scaling"; style="rounded,filled"; fillcolor="#fafafa"; color="{GREY}";
      gw [label="API Gateway ×2\\nsg-gateway: 8080 from sg-alb", fillcolor="{ORANGE_BG}", color="{ORANGE}"];
      svc [label="User · Product · Cart · Order · Payment · Notification\\n(×2 each)  sg-services", fillcolor="{BLUE_BG}", color="{BLUE}"];
      eur [label="Eureka", fillcolor="{VIOLET_BG}", color="{VIOLET}"];
    }}
    subgraph cluster_data {{ label="Private data subnets"; style="rounded,filled"; fillcolor="#fafafa"; color="{GREY}";
      rds [label="RDS MySQL\\nMulti-AZ", shape=cylinder, fillcolor="{GREEN_BG}", color="{GREEN}"];
      ec [label="ElastiCache\\nRedis", shape=cylinder, fillcolor="{GREEN_BG}", color="{GREEN}"];
      docdb [label="DocumentDB\\n(MongoDB API)", shape=cylinder, fillcolor="{GREEN_BG}", color="{GREEN}"];
      msk [label="Amazon MSK\\n(Kafka)", shape=cylinder, fillcolor="{GREEN_BG}", color="{GREEN}"];
      os [label="OpenSearch", shape=cylinder, fillcolor="{GREEN_BG}", color="{GREEN}"];
    }}
  }}
  sm [label="Secrets Manager", fillcolor=white]; cw [label="CloudWatch logs & alarms", fillcolor=white];
  ses [label="Amazon SES", fillcolor=white]; pg [label="Stripe / Razorpay", fillcolor=white];
  users -> r53 -> alb -> gw -> svc; svc -> eur [style=dotted, arrowhead=none];
  svc -> rds; svc -> ec; gw -> ec; svc -> docdb; svc -> msk; gw -> msk; svc -> os;
  svc -> nat [style=dashed]; nat -> ses [style=dashed]; nat -> pg [style=dashed]; pg -> alb [style=dashed, label="webhooks"];
  sm -> svc [style=dashed]; svc -> cw [style=dashed];
"""
    render("aws-deployment", b, rankdir="TB")


def process():
    b = f"""
  node [fillcolor="{BLUE_BG}", color="{BLUE}"];
  a [label="1. Requirements\\nPRD analysis"]; b [label="2. High-level design\\nservices, data stores, Kafka"];
  c [label="3. Low-level design\\nclasses, schemas, APIs"]; d [label="4. Implementation\\nSpring Boot services"];
  e [label="5. Testing\\nunit · integration · end-to-end"]; f [label="6. Benchmark &\\noptimise"];
  g [label="7. Containerise &\\ndeploy (Docker, AWS)"];
  {{rank=same; a; b; c; d;}}
  {{rank=same; e; f; g;}}
  a -> b -> c -> d; d -> e; e -> f -> g; f -> d [style=dashed, label="iterate"]; e -> d [style=dashed, constraint=false];
"""
    render("development-process", b, rankdir="TB")


def search_flow():
    b = f"""
  node [fillcolor="{BLUE_BG}", color="{BLUE}"];
  client [label="Client\\nGET /search?q=lapotp&size=10", fillcolor=white, color="{GREY}"];
  gw [label="API Gateway\\nRouteAccessPolicy: PUBLIC\\nrate limit (Redis)", fillcolor="{ORANGE_BG}", color="{ORANGE}"];
  ctl [label="SearchController\\n(Controller)"]; svc [label="SearchService\\n(Service)"];
  es [label="Elasticsearch\\nmulti_match, fuzziness AUTO\\nname^3, categoryName^2, description", shape=cylinder, fillcolor="{GREEN_BG}", color="{GREEN}"];
  db [label="MySQL fallback\\nname/description LIKE %q%", shape=cylinder, fillcolor="{GREY_BG}", color="{GREY}"];
  dto [label="Page<ProductDto>\\n(View: JSON)"];
  client -> gw [label="1"]; gw -> ctl [label="2  lb://productservice"]; ctl -> svc [label="3"];
  svc -> es [label="4  enabled"]; svc -> db [label="4' disabled", style=dashed]; es -> svc [label="5  hits"];
  svc -> ctl [label="6  Page<Product>"]; ctl -> dto [label="7  map"]; dto -> client [label="8  200 OK", constraint=false];
"""
    render("search-request-flow", b)


if __name__ == "__main__":
    for fn in (architecture, kafka_flow, use_cases, class_user, class_product, class_cart_order, class_payment,
               class_gateway_notification, state_machine, er_diagrams, deployment, process, search_flow):
        fn()
    print("diagrams written to", OUT)
