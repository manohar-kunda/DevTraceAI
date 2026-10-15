// ==============================================================================
// DevTrace AI — Golden Scenario Seed Data (Synthetic)
// All records are synthetic for deterministic demonstration & verification.
// Idempotency: All queries use MERGE with ON CREATE SET and ON MATCH SET.
// ==============================================================================

// --- 1. Services ---
MERGE (s:Service {id: 'service-gateway'})
ON CREATE SET s.name = 'api-gateway', s.team = 'Edge Infrastructure', s.aliases = 'api-gateway,gateway,edge-router,ingress', s.criticalTier = 1, s.serviceType = 'MICROSERVICE', s.source = 'synthetic'
ON MATCH SET s.name = 'api-gateway', s.team = 'Edge Infrastructure', s.aliases = 'api-gateway,gateway,edge-router,ingress', s.criticalTier = 1, s.serviceType = 'MICROSERVICE', s.source = 'synthetic';

MERGE (s:Service {id: 'service-auth'})
ON CREATE SET s.name = 'auth-service', s.team = 'Security', s.aliases = 'auth-service,auth,authentication,jwt,oauth,identity', s.criticalTier = 1, s.serviceType = 'MICROSERVICE', s.source = 'synthetic'
ON MATCH SET s.name = 'auth-service', s.team = 'Security', s.aliases = 'auth-service,auth,authentication,jwt,oauth,identity', s.criticalTier = 1, s.serviceType = 'MICROSERVICE', s.source = 'synthetic';

MERGE (s:Service {id: 'service-checkout'})
ON CREATE SET s.name = 'checkout-service', s.team = 'Checkout Team', s.aliases = 'checkout-service,checkout,cart,web-checkout', s.criticalTier = 1, s.serviceType = 'MICROSERVICE', s.source = 'synthetic'
ON MATCH SET s.name = 'checkout-service', s.team = 'Checkout Team', s.aliases = 'checkout-service,checkout,cart,web-checkout', s.criticalTier = 1, s.serviceType = 'MICROSERVICE', s.source = 'synthetic';

MERGE (s:Service {id: 'service-order'})
ON CREATE SET s.name = 'order-service', s.team = 'Ordering', s.aliases = 'order-service,orders,order-processing,fulfillment', s.criticalTier = 1, s.serviceType = 'MICROSERVICE', s.source = 'synthetic'
ON MATCH SET s.name = 'order-service', s.team = 'Ordering', s.aliases = 'order-service,orders,order-processing,fulfillment', s.criticalTier = 1, s.serviceType = 'MICROSERVICE', s.source = 'synthetic';

MERGE (s:Service {id: 'service-inventory'})
ON CREATE SET s.name = 'inventory-service', s.team = 'Inventory', s.aliases = 'inventory-service,inventory,stock,warehouse', s.criticalTier = 2, s.serviceType = 'MICROSERVICE', s.source = 'synthetic'
ON MATCH SET s.name = 'inventory-service', s.team = 'Inventory', s.aliases = 'inventory-service,inventory,stock,warehouse', s.criticalTier = 2, s.serviceType = 'MICROSERVICE', s.source = 'synthetic';

MERGE (s:Service {id: 'service-payment'})
ON CREATE SET s.name = 'payment-service', s.team = 'Payments Core', s.aliases = 'payment-service,payment,payments,checkout-payment,payment-gateway', s.criticalTier = 1, s.serviceType = 'MICROSERVICE', s.source = 'synthetic'
ON MATCH SET s.name = 'payment-service', s.team = 'Payments Core', s.aliases = 'payment-service,payment,payments,checkout-payment,payment-gateway', s.criticalTier = 1, s.serviceType = 'MICROSERVICE', s.source = 'synthetic';

MERGE (s:Service {id: 'service-notification'})
ON CREATE SET s.name = 'notification-service', s.team = 'Messaging', s.aliases = 'notification-service,notification,notifications,email,sms', s.criticalTier = 3, s.serviceType = 'MICROSERVICE', s.source = 'synthetic'
ON MATCH SET s.name = 'notification-service', s.team = 'Messaging', s.aliases = 'notification-service,notification,notifications,email,sms', s.criticalTier = 3, s.serviceType = 'MICROSERVICE', s.source = 'synthetic';

MERGE (s:Service {id: 'service-postgres-orders'})
ON CREATE SET s.name = 'postgres-orders', s.team = 'Database Infrastructure', s.aliases = 'postgres-orders,orders-db,order-database', s.criticalTier = 1, s.serviceType = 'DATABASE', s.source = 'synthetic'
ON MATCH SET s.name = 'postgres-orders', s.team = 'Database Infrastructure', s.aliases = 'postgres-orders,orders-db,order-database', s.criticalTier = 1, s.serviceType = 'DATABASE', s.source = 'synthetic';

MERGE (s:Service {id: 'service-postgres-payments'})
ON CREATE SET s.name = 'postgres-payments', s.team = 'Database Infrastructure', s.aliases = 'postgres-payments,payments-db,payment-database', s.criticalTier = 1, s.serviceType = 'DATABASE', s.source = 'synthetic'
ON MATCH SET s.name = 'postgres-payments', s.team = 'Database Infrastructure', s.aliases = 'postgres-payments,payments-db,payment-database', s.criticalTier = 1, s.serviceType = 'DATABASE', s.source = 'synthetic';

MERGE (s:Service {id: 'service-redis-cache'})
ON CREATE SET s.name = 'redis-cache', s.team = 'Database Infrastructure', s.aliases = 'redis-cache,cache,redis,session-cache', s.criticalTier = 2, s.serviceType = 'CACHE', s.source = 'synthetic'
ON MATCH SET s.name = 'redis-cache', s.team = 'Database Infrastructure', s.aliases = 'redis-cache,cache,redis,session-cache', s.criticalTier = 2, s.serviceType = 'CACHE', s.source = 'synthetic';

// --- 2. Deployments ---
MERGE (d:Deployment {id: 'dep-checkout-v3.0.0'})
ON CREATE SET d.version = 'v3.0.0', d.deployedAt = 1791453600000, d.environment = 'production', d.deployedBy = 'github-actions', d.source = 'synthetic'
ON MATCH SET d.version = 'v3.0.0', d.deployedAt = 1791453600000, d.environment = 'production', d.deployedBy = 'github-actions', d.source = 'synthetic';

MERGE (d:Deployment {id: 'dep-order-v2.1.0'})
ON CREATE SET d.version = 'v2.1.0', d.deployedAt = 1791885600000, d.environment = 'production', d.deployedBy = 'github-actions', d.source = 'synthetic'
ON MATCH SET d.version = 'v2.1.0', d.deployedAt = 1791885600000, d.environment = 'production', d.deployedBy = 'github-actions', d.source = 'synthetic';

MERGE (d:Deployment {id: 'dep-payment-v2.4.1'})
ON CREATE SET d.version = 'v2.4.1', d.deployedAt = 1792056600000, d.environment = 'production', d.deployedBy = 'github-actions', d.source = 'synthetic'
ON MATCH SET d.version = 'v2.4.1', d.deployedAt = 1792056600000, d.environment = 'production', d.deployedBy = 'github-actions', d.source = 'synthetic';

MERGE (d:Deployment {id: 'dep-notification-v1.8.0'})
ON CREATE SET d.version = 'v1.8.0', d.deployedAt = 1792057500000, d.environment = 'production', d.deployedBy = 'github-actions', d.source = 'synthetic'
ON MATCH SET d.version = 'v1.8.0', d.deployedAt = 1792057500000, d.environment = 'production', d.deployedBy = 'github-actions', d.source = 'synthetic';

MERGE (d:Deployment {id: 'dep-inventory-v1.5.0'})
ON CREATE SET d.version = 'v1.5.0', d.deployedAt = 1792057800000, d.environment = 'production', d.deployedBy = 'github-actions', d.source = 'synthetic'
ON MATCH SET d.version = 'v1.5.0', d.deployedAt = 1792057800000, d.environment = 'production', d.deployedBy = 'github-actions', d.source = 'synthetic';

// --- 3. Error Signatures ---
MERGE (e:ErrorSignature {id: 'err-sig-payment-pool-timeout'})
ON CREATE SET e.pattern = 'JedisConnectionException / PoolTimeoutException: Could not get a resource from the pool', e.errorType = 'TIMEOUT', e.keywords = 'payment timeout,payment timeouts,connection pool timeout,jedis pool timeout,pool timeout,pool exhausted,read timeout', e.source = 'synthetic'
ON MATCH SET e.pattern = 'JedisConnectionException / PoolTimeoutException: Could not get a resource from the pool', e.errorType = 'TIMEOUT', e.keywords = 'payment timeout,payment timeouts,connection pool timeout,jedis pool timeout,pool timeout,pool exhausted,read timeout', e.source = 'synthetic';

MERGE (e:ErrorSignature {id: 'err-sig-jwt-expired'})
ON CREATE SET e.pattern = 'SignatureVerificationException / ClockSkew', e.errorType = 'AUTHENTICATION', e.keywords = 'jwt,expired token,token verification,clock skew,signature verification,auth timeout', e.source = 'synthetic'
ON MATCH SET e.pattern = 'SignatureVerificationException / ClockSkew', e.errorType = 'AUTHENTICATION', e.keywords = 'jwt,expired token,token verification,clock skew,signature verification,auth timeout', e.source = 'synthetic';

MERGE (e:ErrorSignature {id: 'err-sig-smtp-disconnect'})
ON CREATE SET e.pattern = 'SMTPConnectionClosedException', e.errorType = 'NETWORK', e.keywords = 'smtp,disconnect,mail delivery,smtp connection lost', e.source = 'synthetic'
ON MATCH SET e.pattern = 'SMTPConnectionClosedException', e.errorType = 'NETWORK', e.keywords = 'smtp,disconnect,mail delivery,smtp connection lost', e.source = 'synthetic';

// --- 4. Historical Incidents ---
MERGE (i:Incident {id: 'inc-2026-07-20-notif'})
ON CREATE SET i.title = 'Notification worker mail server disconnection', i.status = 'RESOLVED', i.reportedAt = 1784541600000, i.severity = 'P3', i.source = 'synthetic'
ON MATCH SET i.title = 'Notification worker mail server disconnection', i.status = 'RESOLVED', i.reportedAt = 1784541600000, i.severity = 'P3', i.source = 'synthetic';

MERGE (i:Incident {id: 'inc-2026-08-12-pay'})
ON CREATE SET i.title = 'Payment timeout surge during peak checkout', i.status = 'RESOLVED', i.reportedAt = 1786521600000, i.severity = 'P1', i.source = 'synthetic'
ON MATCH SET i.title = 'Payment timeout surge during peak checkout', i.status = 'RESOLVED', i.reportedAt = 1786521600000, i.severity = 'P1', i.source = 'synthetic';

MERGE (i:Incident {id: 'inc-2026-09-01-auth'})
ON CREATE SET i.title = 'Timeouts observed during checkout token verification', i.status = 'RESOLVED', i.reportedAt = 1788253200000, i.severity = 'P2', i.source = 'synthetic'
ON MATCH SET i.title = 'Timeouts observed during checkout token verification', i.status = 'RESOLVED', i.reportedAt = 1788253200000, i.severity = 'P2', i.source = 'synthetic';

// --- 5. Historical Resolutions ---
MERGE (r:Resolution {id: 'res-notif-smtp-failover'})
ON CREATE SET r.title = 'Failover to secondary SMTP relay cluster', r.remediationType = 'CONFIG_CHANGE', r.actionTaken = 'Switched outbound notification host config to standby relay pool.', r.confirmedAt = 1784545200000, r.confirmedBy = 'sre-messaging', r.source = 'synthetic'
ON MATCH SET r.title = 'Failover to secondary SMTP relay cluster', r.remediationType = 'CONFIG_CHANGE', r.actionTaken = 'Switched outbound notification host config to standby relay pool.', r.confirmedAt = 1784545200000, r.confirmedBy = 'sre-messaging', r.source = 'synthetic';

MERGE (r:Resolution {id: 'res-pay-timeout-fix'})
ON CREATE SET r.title = 'Increase Jedis pool timeout and rollback connection limit', r.remediationType = 'CONFIG_CHANGE', r.actionTaken = 'Increased Redis connection pool timeout from 2000ms to 10000ms and rolled back connection pool cap.', r.confirmedAt = 1786525200000, r.confirmedBy = 'sre-oncall', r.source = 'synthetic'
ON MATCH SET r.title = 'Increase Jedis pool timeout and rollback connection limit', r.remediationType = 'CONFIG_CHANGE', r.actionTaken = 'Increased Redis connection pool timeout from 2000ms to 10000ms and rolled back connection pool cap.', r.confirmedAt = 1786525200000, r.confirmedBy = 'sre-oncall', r.source = 'synthetic';

MERGE (r:Resolution {id: 'res-auth-ntp-sync'})
ON CREATE SET r.title = 'Resynchronize NTP clock daemon on auth hosts', r.remediationType = 'RESTART', r.actionTaken = 'Restarted chronyd service and forced immediate NTP time sync across auth host pool.', r.confirmedAt = 1788256800000, r.confirmedBy = 'sec-ops', r.source = 'synthetic'
ON MATCH SET r.title = 'Resynchronize NTP clock daemon on auth hosts', r.remediationType = 'RESTART', r.actionTaken = 'Restarted chronyd service and forced immediate NTP time sync across auth host pool.', r.confirmedAt = 1788256800000, r.confirmedBy = 'sec-ops', r.source = 'synthetic';

// --- 6. Topology Relationships (DEPENDS_ON) ---
MATCH (gw:Service {id: 'service-gateway'}), (auth:Service {id: 'service-auth'})
MERGE (gw)-[r:DEPENDS_ON]->(auth)
ON CREATE SET r.source = 'synthetic';

MATCH (gw:Service {id: 'service-gateway'}), (chk:Service {id: 'service-checkout'})
MERGE (gw)-[r:DEPENDS_ON]->(chk)
ON CREATE SET r.source = 'synthetic';

MATCH (chk:Service {id: 'service-checkout'}), (ord:Service {id: 'service-order'})
MERGE (chk)-[r:DEPENDS_ON]->(ord)
ON CREATE SET r.source = 'synthetic';

MATCH (chk:Service {id: 'service-checkout'}), (inv:Service {id: 'service-inventory'})
MERGE (chk)-[r:DEPENDS_ON]->(inv)
ON CREATE SET r.source = 'synthetic';

MATCH (ord:Service {id: 'service-order'}), (pay:Service {id: 'service-payment'})
MERGE (ord)-[r:DEPENDS_ON]->(pay)
ON CREATE SET r.source = 'synthetic';

MATCH (ord:Service {id: 'service-order'}), (dbOrd:Service {id: 'service-postgres-orders'})
MERGE (ord)-[r:DEPENDS_ON]->(dbOrd)
ON CREATE SET r.source = 'synthetic';

MATCH (pay:Service {id: 'service-payment'}), (dbPay:Service {id: 'service-postgres-payments'})
MERGE (pay)-[r:DEPENDS_ON]->(dbPay)
ON CREATE SET r.source = 'synthetic';

MATCH (pay:Service {id: 'service-payment'}), (cache:Service {id: 'service-redis-cache'})
MERGE (pay)-[r:DEPENDS_ON]->(cache)
ON CREATE SET r.source = 'synthetic';

MATCH (notif:Service {id: 'service-notification'}), (cache:Service {id: 'service-redis-cache'})
MERGE (notif)-[r:DEPENDS_ON]->(cache)
ON CREATE SET r.source = 'synthetic';

// --- 7. Deployment Relationships (DEPLOYED_TO) ---
MATCH (d:Deployment {id: 'dep-checkout-v3.0.0'}), (s:Service {id: 'service-checkout'})
MERGE (d)-[r:DEPLOYED_TO]->(s)
ON CREATE SET r.source = 'synthetic';

MATCH (d:Deployment {id: 'dep-order-v2.1.0'}), (s:Service {id: 'service-order'})
MERGE (d)-[r:DEPLOYED_TO]->(s)
ON CREATE SET r.source = 'synthetic';

MATCH (d:Deployment {id: 'dep-payment-v2.4.1'}), (s:Service {id: 'service-payment'})
MERGE (d)-[r:DEPLOYED_TO]->(s)
ON CREATE SET r.source = 'synthetic';

MATCH (d:Deployment {id: 'dep-notification-v1.8.0'}), (s:Service {id: 'service-notification'})
MERGE (d)-[r:DEPLOYED_TO]->(s)
ON CREATE SET r.source = 'synthetic';

MATCH (d:Deployment {id: 'dep-inventory-v1.5.0'}), (s:Service {id: 'service-inventory'})
MERGE (d)-[r:DEPLOYED_TO]->(s)
ON CREATE SET r.source = 'synthetic';

// --- 8. Incident Symptom Relationships (AFFECTS) ---
MATCH (i:Incident {id: 'inc-2026-07-20-notif'}), (s:Service {id: 'service-notification'})
MERGE (i)-[r:AFFECTS]->(s)
ON CREATE SET r.source = 'synthetic';

MATCH (i:Incident {id: 'inc-2026-08-12-pay'}), (s:Service {id: 'service-payment'})
MERGE (i)-[r:AFFECTS]->(s)
ON CREATE SET r.source = 'synthetic';

MATCH (i:Incident {id: 'inc-2026-09-01-auth'}), (s:Service {id: 'service-auth'})
MERGE (i)-[r:AFFECTS]->(s)
ON CREATE SET r.source = 'synthetic';

// --- 9. Incident Error Signature Relationships (EXHIBITS) ---
MATCH (i:Incident {id: 'inc-2026-07-20-notif'}), (e:ErrorSignature {id: 'err-sig-smtp-disconnect'})
MERGE (i)-[r:EXHIBITS]->(e)
ON CREATE SET r.source = 'synthetic';

MATCH (i:Incident {id: 'inc-2026-08-12-pay'}), (e:ErrorSignature {id: 'err-sig-payment-pool-timeout'})
MERGE (i)-[r:EXHIBITS]->(e)
ON CREATE SET r.source = 'synthetic';

MATCH (i:Incident {id: 'inc-2026-09-01-auth'}), (e:ErrorSignature {id: 'err-sig-jwt-expired'})
MERGE (i)-[r:EXHIBITS]->(e)
ON CREATE SET r.source = 'synthetic';

// --- 10. Resolution Relationships (RESOLVES & APPLIES_TO) ---
MATCH (r:Resolution {id: 'res-notif-smtp-failover'}), (i:Incident {id: 'inc-2026-07-20-notif'})
MERGE (r)-[rel:RESOLVES]->(i)
ON CREATE SET rel.source = 'synthetic';

MATCH (r:Resolution {id: 'res-notif-smtp-failover'}), (s:Service {id: 'service-notification'})
MERGE (r)-[rel:APPLIES_TO]->(s)
ON CREATE SET rel.source = 'synthetic';

MATCH (r:Resolution {id: 'res-pay-timeout-fix'}), (i:Incident {id: 'inc-2026-08-12-pay'})
MERGE (r)-[rel:RESOLVES]->(i)
ON CREATE SET rel.source = 'synthetic';

MATCH (r:Resolution {id: 'res-pay-timeout-fix'}), (s:Service {id: 'service-payment'})
MERGE (r)-[rel:APPLIES_TO]->(s)
ON CREATE SET rel.source = 'synthetic';

MATCH (r:Resolution {id: 'res-auth-ntp-sync'}), (i:Incident {id: 'inc-2026-09-01-auth'})
MERGE (r)-[rel:RESOLVES]->(i)
ON CREATE SET rel.source = 'synthetic';

MATCH (r:Resolution {id: 'res-auth-ntp-sync'}), (s:Service {id: 'service-auth'})
MERGE (r)-[rel:APPLIES_TO]->(s)
ON CREATE SET rel.source = 'synthetic';
