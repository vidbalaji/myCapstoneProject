#!/usr/bin/env python3
"""Prints SQL that bulk-loads N synthetic products (default 200000) into the Product Catalog database.

    python3 scripts/seed_products.py 200000 | docker compose exec -T mysql mysql -uroot -p12345 productservice_aug2026

After loading, rebuild the search index: POST /search/reindex (admin token) on the gateway.
"""
import random
import sys

N = int(sys.argv[1]) if len(sys.argv) > 1 else 200_000
random.seed(42)  # reproducible data set

CATEGORIES = {
    "Laptops": ["Laptop", "Notebook", "Ultrabook", "Chromebook"],
    "Smartphones": ["Smartphone", "Phone", "5G Phone"],
    "Headphones": ["Headphones", "Earbuds", "Headset"],
    "Televisions": ["Smart TV", "OLED TV", "LED TV"],
    "Cameras": ["DSLR Camera", "Mirrorless Camera", "Action Camera"],
    "Watches": ["Smartwatch", "Analog Watch", "Fitness Band"],
    "Kitchen": ["Mixer Grinder", "Electric Kettle", "Air Fryer", "Microwave Oven"],
    "Furniture": ["Office Chair", "Study Table", "Bookshelf", "Sofa"],
    "Footwear": ["Running Shoes", "Sneakers", "Sandals", "Formal Shoes"],
    "Clothing": ["T-Shirt", "Jeans", "Jacket", "Kurta"],
    "Books": ["Novel", "Textbook", "Cookbook", "Biography"],
    "Toys": ["Building Blocks", "Remote Car", "Puzzle", "Doll"],
    "Sports": ["Cricket Bat", "Football", "Yoga Mat", "Badminton Racket"],
    "Beauty": ["Face Wash", "Moisturiser", "Perfume", "Hair Dryer"],
    "Grocery": ["Basmati Rice", "Green Tea", "Olive Oil", "Dark Chocolate"],
    "Gaming": ["Gaming Console", "Gaming Mouse", "Mechanical Keyboard", "Controller"],
    "Storage": ["SSD", "Pen Drive", "External Hard Disk", "Memory Card"],
    "Networking": ["WiFi Router", "Mesh System", "Network Switch"],
    "Appliances": ["Washing Machine", "Refrigerator", "Air Conditioner", "Water Purifier"],
    "Stationery": ["Notebook Pack", "Gel Pens", "Sketch Book", "Desk Organizer"],
}
BRANDS = ["Acme", "Zenith", "Orion", "Nova", "Apex", "Lumen", "Vertex", "Pulse", "Aurora", "Titan",
          "Nimbus", "Quanta", "Sierra", "Vega", "Helix", "Kairo", "Monza", "Rivo", "Sora", "Tejas"]
ADJECTIVES = ["Pro", "Max", "Lite", "Plus", "Ultra", "Neo", "Air", "Prime", "Edge", "Go"]
FEATURES = ["long battery life", "fast charging", "premium build", "lightweight design", "water resistant",
            "energy efficient", "1 year warranty", "eco friendly material", "compact size", "high performance"]

def q(s):
    return "'" + s.replace("'", "''") + "'"

print("SET autocommit=0;")
print("INSERT INTO category (created_at, last_updated_at, state, name, description) VALUES " + ",".join(
    f"(NOW(6), NOW(6), 0, {q('Seed ' + c)}, {q('Seeded ' + c.lower())})" for c in CATEGORIES) + ";")
print("SET @first_cat = LAST_INSERT_ID();")

cat_names = list(CATEGORIES)
batch = []
for i in range(1, N + 1):
    ci = random.randrange(len(cat_names))
    category = cat_names[ci]
    kind = random.choice(CATEGORIES[category])
    name = f"{random.choice(BRANDS)} {kind} {random.choice(ADJECTIVES)} {random.randint(100, 999)}"
    desc = f"{name} with {random.choice(FEATURES)} and {random.choice(FEATURES)}. Model {i}."
    price = round(random.uniform(99, 150000), 2)
    batch.append(f"(NOW(6), NOW(6), 0, {q(name)}, {q(desc)}, NULL, {price}, @first_cat + {ci}, b'1')")
    if len(batch) == 2000 or i == N:
        print("INSERT INTO product (created_at, last_updated_at, state, name, description, image_url, price, "
              "category_id, is_sale_eligible) VALUES " + ",".join(batch) + ";")
        batch = []
print("COMMIT;")
