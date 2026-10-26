# Deploying on AWS

This maps the HLD's components to managed AWS services. The application code needs no changes: every
connection string, credential and feature flag is an environment variable.

| HLD component | AWS service | Notes |
|---|---|---|
| Load balancer | Application Load Balancer (ELB) | Public subnets; TLS via ACM certificate; forwards to the API Gateway only |
| API Gateway (Kong in the HLD) | Spring Cloud Gateway on Elastic Beanstalk / ECS | Kong (or Amazon API Gateway) can replace it; the access rules live in `RouteAccessPolicy` |
| Microservices | Elastic Beanstalk (Docker / Java SE platform) or ECS Fargate | One environment per service, at least 2 instances across 2 AZs, auto scaling on CPU |
| Service discovery | Eureka on Beanstalk, or ECS Service Connect / Cloud Map | With Cloud Map, set `eureka.client.enabled=false` and use DNS names |
| MySQL | Amazon RDS for MySQL 8 (Multi-AZ) | One database per service: `userservice_aug2026`, `productservice_aug2026`, `orderservice_aug2026`, `paymentservice_aug2026` |
| MongoDB | Amazon DocumentDB (MongoDB-compatible) or MongoDB Atlas | `MONGODB_URI` for the cart service |
| Redis | Amazon ElastiCache for Redis | Cart cache, gateway token cache, rate-limit buckets |
| Kafka | Amazon MSK | 3 brokers across 3 AZs; replication factor 3 for production topics |
| Elasticsearch | Amazon OpenSearch Service | `ELASTICSEARCH_URIS`; the product index is rebuilt with `POST /search/reindex` |
| Email | Amazon SES (SMTP interface) | `SMTP_HOST=email-smtp.<region>.amazonaws.com`, SES SMTP credentials |
| Secrets | AWS Secrets Manager / SSM Parameter Store | `JWT_SECRET`, DB passwords, Stripe/Razorpay keys, SMTP credentials |

## Network layout (VPC)

```
VPC 10.0.0.0/16 (2 Availability Zones)
├── Public subnets   10.0.1.0/24, 10.0.2.0/24   ALB, NAT gateways
├── App subnets      10.0.11.0/24, 10.0.12.0/24 Beanstalk/ECS instances (API Gateway + services)
└── Data subnets     10.0.21.0/24, 10.0.22.0/24 RDS, DocumentDB, ElastiCache, MSK, OpenSearch
```

## Security groups (least privilege)

| Group | Inbound from | Ports |
|---|---|---|
| `sg-alb` | 0.0.0.0/0 | 443 (80 redirects to 443) |
| `sg-gateway` | `sg-alb` | 8080 |
| `sg-services` | `sg-gateway`, `sg-services` | 8081–8086, 8761 |
| `sg-rds` | `sg-services` | 3306 |
| `sg-docdb` | `sg-services` | 27017 |
| `sg-redis` | `sg-gateway`, `sg-services` | 6379 |
| `sg-msk` | `sg-gateway`, `sg-services` | 9092/9094 |
| `sg-opensearch` | `sg-services` | 443 |

Only the ALB is reachable from the internet. Services trust the `X-User-*` headers because only the
gateway can reach them, and the gateway strips any copies a client sends.

## Steps (Elastic Beanstalk)

1. Create the VPC, subnets and security groups above. Then create RDS, DocumentDB, ElastiCache, MSK and
   OpenSearch in the data subnets.
2. Store secrets in Secrets Manager, and reference them as Beanstalk environment properties.
3. `./build-all.sh -DskipTests`, then for each service:
   `eb init` (Java/Docker platform), `eb create <service>-prod --vpc ... --elb-type application` (the
   gateway gets the public ALB; internal services get an internal load balancer or none). Then `eb deploy`.
4. Set environment properties per service, for example for the order service:
   `DB_URL=jdbc:mysql://<rds-endpoint>:3306/orderservice_aug2026`, `KAFKA_BOOTSTRAP_SERVERS=<msk brokers>`,
   `EUREKA_URL=http://<discovery-internal-url>:8761/eureka/`.
5. Point the Stripe/Razorpay webhooks at `https://<domain>/stripeWebhook` and
   `https://<domain>/payment/webhook/razorpay`, and set `PUBLIC_BASE_URL=https://<domain>`.
6. Run `POST /search/reindex` once (admin token) to build the OpenSearch index.

## Cost notes

The smallest production-like setup (2× t3.small per service, db.t3.medium Multi-AZ RDS, cache.t3.micro,
3× kafka.t3.small MSK, a small OpenSearch domain and a small DocumentDB cluster) is dominated by MSK,
OpenSearch and DocumentDB. For a student or demo deployment, run Kafka, MongoDB and Elasticsearch on one EC2
instance with `docker-compose.yml`, and keep only RDS and ElastiCache managed.
