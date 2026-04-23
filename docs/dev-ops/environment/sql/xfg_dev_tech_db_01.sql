# ************************************************************
# Sequel Ace SQL dump
# 版本号： 20050
#
# https://sequel-ace.com/
# https://github.com/Sequel-Ace/Sequel-Ace
#
# 主机: 127.0.0.1 (MySQL 8.0.32)
# 数据库: xfg_dev_tech_db_01
# 生成时间: 2023-11-02 07:42:32 +0000
# ************************************************************


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
SET NAMES utf8mb4;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE='NO_AUTO_VALUE_ON_ZERO', SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE database if NOT EXISTS `xfg_dev_tech_db_01` default character set utf8mb4 collate utf8mb4_0900_ai_ci;
use `xfg_dev_tech_db_01`;

# 转储表 user_order_0
# ------------------------------------------------------------

DROP TABLE IF EXISTS `user_order_0`;

CREATE TABLE `user_order_0` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID；【必须保留自增ID，不要将一些有随机特性的字段值设计为主键，例如order_id，会导致innodb内部page分裂和大量随机I/O，性能下降】int 大约21亿左右，超过会报错。bigint 大约9千亿左右。',
  `user_name` varchar(64) NOT NULL COMMENT '用户姓名；',
  `user_id` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户编号；',
  `user_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户电话；使用varchar(20)存储手机号，不要使用整型。手机号不会做数学计算、涉及到区号或者国家代号，可能出现+-()、支持模糊查询，例如：like“135%”',
  `sku` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品编号',
  `sku_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `order_id` varchar(64) NOT NULL COMMENT '订单ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '商品数量；整形定义中不显示规定显示长度，比如使用 INT，而不使用 INT(4)',
  `unit_price` decimal(10,2) NOT NULL COMMENT '商品价格；小数类型为 decimal，禁止使用 float、double',
  `discount_amount` decimal(10,2) NOT NULL COMMENT '折扣金额；',
  `tax` decimal(4,2) NOT NULL COMMENT '费率金额；',
  `total_amount` decimal(10,2) NOT NULL COMMENT '支付金额；(商品的总金额 - 折扣) * (1 - 费率)',
  `order_date` datetime NOT NULL COMMENT '订单日期；timestamp的时间范围在1970-01-01 00:00:01到2038-01-01 00:00:00之间',
  `order_status` tinyint(1) NOT NULL COMMENT '订单状态；0 创建、1完成、2掉单、3关单 【不要使用 enum 要使用 tinyint 替代。0-80 范围，都可以使用 tinyint】',
  `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删单；0未删除，1已删除 【表达是否概念的字段必须使用is_】',
  `uuid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '唯一索引；分布式下全局唯一，用于binlog 同步 ES 方便使用',
  `ipv4` int unsigned NOT NULL DEFAULT '2130706433' COMMENT '设备地址；存储IPV4地址，通过MySQL 函数转换，inet_ntoa、inet_aton 示例；SELECT INET_ATON(‘209.207.224.40′); 3520061480 SELECT INET_NTOA(3520061480); 209.207.224.40所有字段定义为NOT NULL，并设置默认值，因为null值的字段会导致每一行都占用额外存储空间\\n数据迁移容易出错，在聚合函数计算结果偏差（如count结果不准）并且null的列使索引/索引统计/值比较都更加复杂，MySQL内部需要进行特殊处理，表中有较多空字段的时候，数据库性能下降严重。开发中null只能采用is null或is not null检索，而不能采用=、in、<、<>、!=、not in这些操作符号。如：where name!=’abc’，如果存在name为null值的记录，查询结果就不会包含name为null值的记录',
  `ipv6` varbinary(16) NOT NULL COMMENT '设备地址；存储IPV6地址，VARBINARY(16)  插入：INET6_ATON(''2001:0db8:85a3:0000:0000:8a2e:0370:7334'') 查询：SELECT INET6_NTOA(ip_address) ',
  `ext_data` json NOT NULL COMMENT '扩展数据；记录下单时用户的设备环境等信息(核心业务字段，要单独拆表)。【select user_name, ext_data, ext_data->>''$.device'', ext_data->>''$.device.machine'' from `user_order`;】',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_orderid` (`order_id`),
  UNIQUE KEY `uq_uuid` (`uuid`),
  KEY `idx_order_date` (`order_date`),
  KEY `idx_sku_unit_price_total_amount` (`sku`,`unit_price`,`total_amount`)
) ENGINE=InnoDB AUTO_INCREMENT=144 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

LOCK TABLES `user_order_0` WRITE;
/*!40000 ALTER TABLE `user_order_0` DISABLE KEYS */;

INSERT INTO `user_order_0` (`id`, `user_name`, `user_id`, `user_mobile`, `sku`, `sku_name`, `order_id`, `quantity`, `unit_price`, `discount_amount`, `tax`, `total_amount`, `order_date`, `order_status`, `is_delete`, `uuid`, `ipv4`, `ipv6`, `ext_data`, `update_time`, `create_time`)
VALUES
	(1,'小傅哥','xfg_eKJVpO','+86 13521408***','13811216','《手写MyBatis：渐进式源码实践》','13232577250',1,128.00,50.00,0.00,78.00,'2023-11-02 07:37:51',0,0,'bad18b73d64a41e5a6c66ffde5c86620',2130706433,X'20010DB885A3000000008A2E03707334','{\"device\": {\"machine\": \"IPhone 14 Pro\", \"location\": \"shanghai\"}}','2023-11-02 15:38:00','2023-11-02 15:38:00'),
	(2,'小傅哥','xfg_KigNVH','+86 13521408***','13811216','《手写MyBatis：渐进式源码实践》','06196275702',1,128.00,50.00,0.00,78.00,'2023-11-02 07:38:00',0,0,'21efcbb490de4f56b64ae44b05666513',2130706433,X'20010DB885A3000000008A2E03707334','{\"device\": {\"machine\": \"IPhone 14 Pro\", \"location\": \"shanghai\"}}','2023-11-02 15:38:00','2023-11-02 15:38:00');

/*!40000 ALTER TABLE `user_order_0` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 user_order_1
# ------------------------------------------------------------

DROP TABLE IF EXISTS `user_order_1`;

CREATE TABLE `user_order_1` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID；【必须保留自增ID，不要将一些有随机特性的字段值设计为主键，例如order_id，会导致innodb内部page分裂和大量随机I/O，性能下降】int 大约21亿左右，超过会报错。bigint 大约9千亿左右。',
  `user_name` varchar(64) NOT NULL COMMENT '用户姓名；',
  `user_id` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户编号；',
  `user_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户电话；使用varchar(20)存储手机号，不要使用整型。手机号不会做数学计算、涉及到区号或者国家代号，可能出现+-()、支持模糊查询，例如：like“135%”',
  `sku` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品编号',
  `sku_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `order_id` varchar(64) NOT NULL COMMENT '订单ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '商品数量；整形定义中不显示规定显示长度，比如使用 INT，而不使用 INT(4)',
  `unit_price` decimal(10,2) NOT NULL COMMENT '商品价格；小数类型为 decimal，禁止使用 float、double',
  `discount_amount` decimal(10,2) NOT NULL COMMENT '折扣金额；',
  `tax` decimal(4,2) NOT NULL COMMENT '费率金额；',
  `total_amount` decimal(10,2) NOT NULL COMMENT '支付金额；(商品的总金额 - 折扣) * (1 - 费率)',
  `order_date` datetime NOT NULL COMMENT '订单日期；timestamp的时间范围在1970-01-01 00:00:01到2038-01-01 00:00:00之间',
  `order_status` tinyint(1) NOT NULL COMMENT '订单状态；0 创建、1完成、2掉单、3关单 【不要使用 enum 要使用 tinyint 替代。0-80 范围，都可以使用 tinyint】',
  `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删单；0未删除，1已删除 【表达是否概念的字段必须使用is_】',
  `uuid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '唯一索引；分布式下全局唯一，用于binlog 同步 ES 方便使用',
  `ipv4` int unsigned NOT NULL DEFAULT '2130706433' COMMENT '设备地址；存储IPV4地址，通过MySQL 函数转换，inet_ntoa、inet_aton 示例；SELECT INET_ATON(‘209.207.224.40′); 3520061480 SELECT INET_NTOA(3520061480); 209.207.224.40所有字段定义为NOT NULL，并设置默认值，因为null值的字段会导致每一行都占用额外存储空间\\n数据迁移容易出错，在聚合函数计算结果偏差（如count结果不准）并且null的列使索引/索引统计/值比较都更加复杂，MySQL内部需要进行特殊处理，表中有较多空字段的时候，数据库性能下降严重。开发中null只能采用is null或is not null检索，而不能采用=、in、<、<>、!=、not in这些操作符号。如：where name!=’abc’，如果存在name为null值的记录，查询结果就不会包含name为null值的记录',
  `ipv6` varbinary(16) NOT NULL COMMENT '设备地址；存储IPV6地址，VARBINARY(16)  插入：INET6_ATON(''2001:0db8:85a3:0000:0000:8a2e:0370:7334'') 查询：SELECT INET6_NTOA(ip_address) ',
  `ext_data` json NOT NULL COMMENT '扩展数据；记录下单时用户的设备环境等信息(核心业务字段，要单独拆表)。【select user_name, ext_data, ext_data->>''$.device'', ext_data->>''$.device.machine'' from `user_order`;】',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_orderid` (`order_id`),
  UNIQUE KEY `uq_uuid` (`uuid`),
  KEY `idx_order_date` (`order_date`),
  KEY `idx_sku_unit_price_total_amount` (`sku`,`unit_price`,`total_amount`)
) ENGINE=InnoDB AUTO_INCREMENT=127 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

LOCK TABLES `user_order_1` WRITE;
/*!40000 ALTER TABLE `user_order_1` DISABLE KEYS */;

INSERT INTO `user_order_1` (`id`, `user_name`, `user_id`, `user_mobile`, `sku`, `sku_name`, `order_id`, `quantity`, `unit_price`, `discount_amount`, `tax`, `total_amount`, `order_date`, `order_status`, `is_delete`, `uuid`, `ipv4`, `ipv6`, `ext_data`, `update_time`, `create_time`)
VALUES
	(1,'小傅哥','xfg_SNvNdB','+86 13521408***','13811216','《手写MyBatis：渐进式源码实践》','08440801977',1,128.00,50.00,0.00,78.00,'2023-11-02 07:38:55',0,0,'4044fe46fabe498b8905a4f620f36076',2130706433,X'20010DB885A3000000008A2E03707334','{\"device\": {\"machine\": \"IPhone 14 Pro\", \"location\": \"shanghai\"}}','2023-11-02 15:38:54','2023-11-02 15:38:54'),
	(2,'小傅哥','xfg_ccwftd','+86 13521408***','13811216','《手写MyBatis：渐进式源码实践》','96970982688',1,128.00,50.00,0.00,78.00,'2023-11-02 07:38:55',0,0,'90c02e7687bc4bc7a0b9d4eebee78d7a',2130706433,X'20010DB885A3000000008A2E03707334','{\"device\": {\"machine\": \"IPhone 14 Pro\", \"location\": \"shanghai\"}}','2023-11-02 15:38:55','2023-11-02 15:38:55');

/*!40000 ALTER TABLE `user_order_1` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 user_order_2
# ------------------------------------------------------------

DROP TABLE IF EXISTS `user_order_2`;

CREATE TABLE `user_order_2` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID；【必须保留自增ID，不要将一些有随机特性的字段值设计为主键，例如order_id，会导致innodb内部page分裂和大量随机I/O，性能下降】int 大约21亿左右，超过会报错。bigint 大约9千亿左右。',
  `user_name` varchar(64) NOT NULL COMMENT '用户姓名；',
  `user_id` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户编号；',
  `user_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户电话；使用varchar(20)存储手机号，不要使用整型。手机号不会做数学计算、涉及到区号或者国家代号，可能出现+-()、支持模糊查询，例如：like“135%”',
  `sku` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品编号',
  `sku_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `order_id` varchar(64) NOT NULL COMMENT '订单ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '商品数量；整形定义中不显示规定显示长度，比如使用 INT，而不使用 INT(4)',
  `unit_price` decimal(10,2) NOT NULL COMMENT '商品价格；小数类型为 decimal，禁止使用 float、double',
  `discount_amount` decimal(10,2) NOT NULL COMMENT '折扣金额；',
  `tax` decimal(4,2) NOT NULL COMMENT '费率金额；',
  `total_amount` decimal(10,2) NOT NULL COMMENT '支付金额；(商品的总金额 - 折扣) * (1 - 费率)',
  `order_date` datetime NOT NULL COMMENT '订单日期；timestamp的时间范围在1970-01-01 00:00:01到2038-01-01 00:00:00之间',
  `order_status` tinyint(1) NOT NULL COMMENT '订单状态；0 创建、1完成、2掉单、3关单 【不要使用 enum 要使用 tinyint 替代。0-80 范围，都可以使用 tinyint】',
  `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删单；0未删除，1已删除 【表达是否概念的字段必须使用is_】',
  `uuid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '唯一索引；分布式下全局唯一，用于binlog 同步 ES 方便使用',
  `ipv4` int unsigned NOT NULL DEFAULT '2130706433' COMMENT '设备地址；存储IPV4地址，通过MySQL 函数转换，inet_ntoa、inet_aton 示例；SELECT INET_ATON(‘209.207.224.40′); 3520061480 SELECT INET_NTOA(3520061480); 209.207.224.40所有字段定义为NOT NULL，并设置默认值，因为null值的字段会导致每一行都占用额外存储空间\\n数据迁移容易出错，在聚合函数计算结果偏差（如count结果不准）并且null的列使索引/索引统计/值比较都更加复杂，MySQL内部需要进行特殊处理，表中有较多空字段的时候，数据库性能下降严重。开发中null只能采用is null或is not null检索，而不能采用=、in、<、<>、!=、not in这些操作符号。如：where name!=’abc’，如果存在name为null值的记录，查询结果就不会包含name为null值的记录',
  `ipv6` varbinary(16) NOT NULL COMMENT '设备地址；存储IPV6地址，VARBINARY(16)  插入：INET6_ATON(''2001:0db8:85a3:0000:0000:8a2e:0370:7334'') 查询：SELECT INET6_NTOA(ip_address) ',
  `ext_data` json NOT NULL COMMENT '扩展数据；记录下单时用户的设备环境等信息(核心业务字段，要单独拆表)。【select user_name, ext_data, ext_data->>''$.device'', ext_data->>''$.device.machine'' from `user_order`;】',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_orderid` (`order_id`),
  UNIQUE KEY `uq_uuid` (`uuid`),
  KEY `idx_order_date` (`order_date`),
  KEY `idx_sku_unit_price_total_amount` (`sku`,`unit_price`,`total_amount`)
) ENGINE=InnoDB AUTO_INCREMENT=127 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

LOCK TABLES `user_order_2` WRITE;
/*!40000 ALTER TABLE `user_order_2` DISABLE KEYS */;

INSERT INTO `user_order_2` (`id`, `user_name`, `user_id`, `user_mobile`, `sku`, `sku_name`, `order_id`, `quantity`, `unit_price`, `discount_amount`, `tax`, `total_amount`, `order_date`, `order_status`, `is_delete`, `uuid`, `ipv4`, `ipv6`, `ext_data`, `update_time`, `create_time`)
VALUES
	(1,'小傅哥','xfg_rAZmon','+86 13521408***','13811216','《手写MyBatis：渐进式源码实践》','76407784565',1,128.00,50.00,0.00,78.00,'2023-11-02 07:38:00',0,0,'0c137db910fd430b9bab307b45b64369',2130706433,X'20010DB885A3000000008A2E03707334','{\"device\": {\"machine\": \"IPhone 14 Pro\", \"location\": \"shanghai\"}}','2023-11-02 15:38:00','2023-11-02 15:38:00'),
	(2,'小傅哥','xfg_jbUupM','+86 13521408***','13811216','《手写MyBatis：渐进式源码实践》','29649461429',1,128.00,50.00,0.00,78.00,'2023-11-02 07:38:00',0,0,'f3ab7b04065a4c6cb562942c8fcf3282',2130706433,X'20010DB885A3000000008A2E03707334','{\"device\": {\"machine\": \"IPhone 14 Pro\", \"location\": \"shanghai\"}}','2023-11-02 15:38:00','2023-11-02 15:38:00');

/*!40000 ALTER TABLE `user_order_2` ENABLE KEYS */;
UNLOCK TABLES;


# 转储表 user_order_3
# ------------------------------------------------------------

DROP TABLE IF EXISTS `user_order_3`;

CREATE TABLE `user_order_3` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID；【必须保留自增ID，不要将一些有随机特性的字段值设计为主键，例如order_id，会导致innodb内部page分裂和大量随机I/O，性能下降】int 大约21亿左右，超过会报错。bigint 大约9千亿左右。',
  `user_name` varchar(64) NOT NULL COMMENT '用户姓名；',
  `user_id` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户编号；',
  `user_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户电话；使用varchar(20)存储手机号，不要使用整型。手机号不会做数学计算、涉及到区号或者国家代号，可能出现+-()、支持模糊查询，例如：like“135%”',
  `sku` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品编号',
  `sku_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `order_id` varchar(64) NOT NULL COMMENT '订单ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '商品数量；整形定义中不显示规定显示长度，比如使用 INT，而不使用 INT(4)',
  `unit_price` decimal(10,2) NOT NULL COMMENT '商品价格；小数类型为 decimal，禁止使用 float、double',
  `discount_amount` decimal(10,2) NOT NULL COMMENT '折扣金额；',
  `tax` decimal(4,2) NOT NULL COMMENT '费率金额；',
  `total_amount` decimal(10,2) NOT NULL COMMENT '支付金额；(商品的总金额 - 折扣) * (1 - 费率)',
  `order_date` datetime NOT NULL COMMENT '订单日期；timestamp的时间范围在1970-01-01 00:00:01到2038-01-01 00:00:00之间',
  `order_status` tinyint(1) NOT NULL COMMENT '订单状态；0 创建、1完成、2掉单、3关单 【不要使用 enum 要使用 tinyint 替代。0-80 范围，都可以使用 tinyint】',
  `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删单；0未删除，1已删除 【表达是否概念的字段必须使用is_】',
  `uuid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '唯一索引；分布式下全局唯一，用于binlog 同步 ES 方便使用',
  `ipv4` int unsigned NOT NULL DEFAULT '2130706433' COMMENT '设备地址；存储IPV4地址，通过MySQL 函数转换，inet_ntoa、inet_aton 示例；SELECT INET_ATON(‘209.207.224.40′); 3520061480 SELECT INET_NTOA(3520061480); 209.207.224.40所有字段定义为NOT NULL，并设置默认值，因为null值的字段会导致每一行都占用额外存储空间\\n数据迁移容易出错，在聚合函数计算结果偏差（如count结果不准）并且null的列使索引/索引统计/值比较都更加复杂，MySQL内部需要进行特殊处理，表中有较多空字段的时候，数据库性能下降严重。开发中null只能采用is null或is not null检索，而不能采用=、in、<、<>、!=、not in这些操作符号。如：where name!=’abc’，如果存在name为null值的记录，查询结果就不会包含name为null值的记录',
  `ipv6` varbinary(16) NOT NULL COMMENT '设备地址；存储IPV6地址，VARBINARY(16)  插入：INET6_ATON(''2001:0db8:85a3:0000:0000:8a2e:0370:7334'') 查询：SELECT INET6_NTOA(ip_address) ',
  `ext_data` json NOT NULL COMMENT '扩展数据；记录下单时用户的设备环境等信息(核心业务字段，要单独拆表)。【select user_name, ext_data, ext_data->>''$.device'', ext_data->>''$.device.machine'' from `user_order`;】',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_orderid` (`order_id`),
  UNIQUE KEY `uq_uuid` (`uuid`),
  KEY `idx_order_date` (`order_date`),
  KEY `idx_sku_unit_price_total_amount` (`sku`,`unit_price`,`total_amount`)
) ENGINE=InnoDB AUTO_INCREMENT=148 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

LOCK TABLES `user_order_3` WRITE;
/*!40000 ALTER TABLE `user_order_3` DISABLE KEYS */;

INSERT INTO `user_order_3` (`id`, `user_name`, `user_id`, `user_mobile`, `sku`, `sku_name`, `order_id`, `quantity`, `unit_price`, `discount_amount`, `tax`, `total_amount`, `order_date`, `order_status`, `is_delete`, `uuid`, `ipv4`, `ipv6`, `ext_data`, `update_time`, `create_time`)
VALUES
	(1,'小傅哥','xfg_JsEUcu','+86 13521408***','13811216','《手写MyBatis：渐进式源码实践》','21630931065',1,128.00,50.00,0.00,78.00,'2023-11-02 07:38:00',0,0,'7f595d4df9954b6788c987a20e23b765',2130706433,X'20010DB885A3000000008A2E03707334','{\"device\": {\"machine\": \"IPhone 14 Pro\", \"location\": \"shanghai\"}}','2023-11-02 15:38:00','2023-11-02 15:38:00'),
	(2,'小傅哥','xfg_DhmjDx','+86 13521408***','13811216','《手写MyBatis：渐进式源码实践》','86253998065',1,128.00,50.00,0.00,78.00,'2023-11-02 07:38:00',0,0,'42dcfeae7c2c45eb9d6fb921a8a71104',2130706433,X'20010DB885A3000000008A2E03707334','{\"device\": {\"machine\": \"IPhone 14 Pro\", \"location\": \"shanghai\"}}','2023-11-02 15:38:00','2023-11-02 15:38:00');

/*!40000 ALTER TABLE `user_order_3` ENABLE KEYS */;
UNLOCK TABLES;



/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
