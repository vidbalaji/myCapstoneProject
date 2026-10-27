#!/usr/bin/env python3
"""Prints SQL that loads synthetic order history: USERS users x ORDERS_PER_USER orders x 2 items each.

    python3 scripts/seed_orders.py 3000 100 | docker compose exec -T mysql mysql -uroot -p12345 orderservice_aug2026

Users get ids 500001.. so they never clash with real accounts. Used for the order-history benchmark.
"""
import random
import sys

USERS = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
PER_USER = int(sys.argv[2]) if len(sys.argv) > 2 else 100
FIRST_ORDER_ID = 1_000_000
random.seed(11)

print("SET autocommit=0;")
orders, items, order_id = [], [], FIRST_ORDER_ID
for n in range(USERS * PER_USER):
    user = 500001 + random.randrange(USERS)   # interleave users, as real traffic would
    created = f"DATE_SUB(NOW(6), INTERVAL {random.randint(0, 365 * 24 * 60)} MINUTE)"
    a, b = round(random.uniform(99, 50000), 2), round(random.uniform(99, 50000), 2)
    orders.append(f"({order_id}, {created}, NOW(6), 'INR', 'Bengaluru', 'User {user}', '1 Main Road', NULL, NULL,"
                  f" '560001', 'KA', NULL, 'UPI', 'DELIVERED', {a + b}, 'u{user}@example.com', {user})")
    items.append(f"(NOW(6), NOW(6), {random.randint(1, 200000)}, 'Item A', 1, {a}, {order_id})")
    items.append(f"(NOW(6), NOW(6), {random.randint(1, 200000)}, 'Item B', 1, {b}, {order_id})")
    order_id += 1
    if len(orders) == 2000:
        print("INSERT INTO orders (id, created_at, last_updated_at, currency, city, full_name, line1, line2, "
              "phone_number, pincode, state, payment_link, payment_method, status, total_amount, user_email, user_id)"
              " VALUES " + ",".join(orders) + ";")
        print("INSERT INTO order_item (created_at, last_updated_at, product_id, product_name, quantity, unit_price,"
              " order_id) VALUES " + ",".join(items) + ";")
        orders, items = [], []
if orders:
    print("INSERT INTO orders (id, created_at, last_updated_at, currency, city, full_name, line1, line2, "
          "phone_number, pincode, state, payment_link, payment_method, status, total_amount, user_email, user_id)"
          " VALUES " + ",".join(orders) + ";")
    print("INSERT INTO order_item (created_at, last_updated_at, product_id, product_name, quantity, unit_price,"
          " order_id) VALUES " + ",".join(items) + ";")
print("COMMIT;")
