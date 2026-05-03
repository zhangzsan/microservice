/*
SQLyog Ultimate v13.1.1 (64 bit)
MySQL - 5.6.51-log : Database - order_db
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`order_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 */;

USE `order_db`;

/*Table structure for table `t_order` */

DROP TABLE IF EXISTS `t_order`;

CREATE TABLE `t_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `quantity` int(11) NOT NULL COMMENT '购买数量',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '订单状态:0待支付,1已支付,2已取消,3已超时',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` int(11) DEFAULT '0' COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB AUTO_INCREMENT=12314 DEFAULT CHARSET=utf8mb4;

/*Data for the table `t_order` */

insert  into `t_order`(`id`,`order_no`,`user_id`,`product_id`,`quantity`,`amount`,`status`,`created_time`,`updated_time`,`version`) values 
(12300,'ORD2026050300000036',1,1,2,9.90,1,'2026-05-03 16:39:22','2026-05-03 16:39:30',0),
(12301,'ORD2026050300000037',1,1,2,9.90,1,'2026-05-03 16:44:46','2026-05-03 16:44:53',0),
(12302,'ORD2026050300000038',1,1,2,9.90,1,'2026-05-03 16:50:13','2026-05-03 16:50:20',0),
(12303,'ORD2026050300000039',1,1,2,9.90,1,'2026-05-03 16:51:02','2026-05-03 16:52:48',0),
(12304,'ORD2026050300000040',1,1,2,9.90,1,'2026-05-03 16:56:07','2026-05-03 16:56:18',0),
(12305,'ORD2026050300000041',1,1,2,9.90,1,'2026-05-03 17:02:59','2026-05-03 17:03:08',0),
(12306,'ORD2026050300000042',1,1,2,9.90,1,'2026-05-03 17:10:53','2026-05-03 17:10:59',0),
(12307,'ORD2026050300000043',1,1,2,9.90,1,'2026-05-03 17:16:24','2026-05-03 17:16:30',0),
(12308,'ORD2026050300000044',1,1,2,9.90,1,'2026-05-03 17:21:12','2026-05-03 17:21:20',0),
(12309,'ORD2026050300000045',1,1,2,9.90,1,'2026-05-03 17:25:50','2026-05-03 17:26:00',0),
(12310,'ORD2026050300000046',1,1,2,9.90,0,'2026-05-03 17:27:09','2026-05-03 17:27:09',0),
(12311,'ORD2026050300000047',1,1,2,9.90,0,'2026-05-03 17:27:10','2026-05-03 17:27:10',0),
(12312,'ORD2026050300000048',1,1,2,9.90,0,'2026-05-03 17:27:12','2026-05-03 17:27:12',0),
(12313,'ORD2026050300000049',1,1,2,9.90,0,'2026-05-03 17:27:13','2026-05-03 17:27:13',0);

/*Table structure for table `t_order_operation_log` */

DROP TABLE IF EXISTS `t_order_operation_log`;

CREATE TABLE `t_order_operation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `operation_type` tinyint(4) NOT NULL COMMENT '操作类型: 1-超时取消 2-支付',
  `operation_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '操作状态: 0-处理中 1-成功 2-失败',
  `retry_count` int(11) DEFAULT '0' COMMENT '重试次数',
  `max_retry_count` int(11) DEFAULT '3' COMMENT '最大重试次数',
  `request_data` text COMMENT '请求数据JSON',
  `error_message` text COMMENT '错误信息',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_operation` (`order_no`,`operation_type`),
  KEY `idx_retry` (`operation_status`,`next_retry_time`)
) ENGINE=InnoDB AUTO_INCREMENT=8339 DEFAULT CHARSET=utf8mb4 COMMENT='订单操作日志表';

/*Data for the table `t_order_operation_log` */

/*Table structure for table `t_order_timeout_message_log` */

DROP TABLE IF EXISTS `t_order_timeout_message_log`;

CREATE TABLE `t_order_timeout_message_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `message_id` varchar(64) NOT NULL COMMENT '消息ID',
  `processed` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否已处理: 0-未处理, 1-已处理',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_message_id` (`message_id`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COMMENT='订单超时消息消费记录表';

/*Data for the table `t_order_timeout_message_log` */

insert  into `t_order_timeout_message_log`(`id`,`order_no`,`message_id`,`processed`,`created_time`,`updated_time`) values 
(35,'ORD2026050300000035','ORD2026050300000035',1,'2026-05-03 16:58:37','2026-05-03 16:58:37'),
(36,'ORD2026050300000036','ORD2026050300000036',1,'2026-05-03 17:09:23','2026-05-03 17:09:23'),
(37,'ORD2026050300000037','ORD2026050300000037',1,'2026-05-03 17:14:46','2026-05-03 17:14:46'),
(38,'ORD2026050300000038','ORD2026050300000038',1,'2026-05-03 17:20:13','2026-05-03 17:20:13'),
(39,'ORD2026050300000039','ORD2026050300000039',1,'2026-05-03 17:21:02','2026-05-03 17:21:02'),
(40,'ORD2026050300000040','ORD2026050300000040',1,'2026-05-03 17:26:07','2026-05-03 17:26:07');

/*Table structure for table `t_payment_record` */

DROP TABLE IF EXISTS `t_payment_record`;

CREATE TABLE `t_payment_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `transaction_id` varchar(128) NOT NULL COMMENT '交易流水号',
  `third_party_trade_no` varchar(128) DEFAULT NULL COMMENT '第三方支付平台交易号(支付宝/微信)',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '支付金额',
  `pay_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '支付状态: 0-处理中 1-成功 2-失败',
  `pay_channel` tinyint(4) DEFAULT NULL COMMENT '支付渠道',
  `channel_name` varchar(32) DEFAULT NULL COMMENT '渠道名称: ALIPAY/WECHAT',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `pay_expire_time` datetime DEFAULT NULL COMMENT '支付过期时间',
  `version` int(11) DEFAULT '0' COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_transaction_id` (`transaction_id`),
  KEY `idx_order_status` (`order_no`,`pay_status`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

/*Data for the table `t_payment_record` */

insert  into `t_payment_record`(`id`,`order_no`,`transaction_id`,`third_party_trade_no`,`pay_amount`,`pay_status`,`pay_channel`,`channel_name`,`created_time`,`updated_time`,`pay_expire_time`,`version`) values 
(22,'ORD2026050300000036','TXN1777797572290ORD2026050300000036',NULL,9.90,1,1,NULL,'2026-05-03 16:39:32','2026-05-03 16:39:32',NULL,0),
(23,'ORD2026050300000037','TXN1777797893890ORD2026050300000037',NULL,9.90,1,1,NULL,'2026-05-03 16:44:54','2026-05-03 16:44:54',NULL,0),
(24,'ORD2026050300000038','TXN1777798220162ORD2026050300000038',NULL,9.90,1,1,NULL,'2026-05-03 16:50:20','2026-05-03 16:50:20',NULL,0),
(25,'ORD2026050300000039','TXN1777798368933ORD2026050300000039',NULL,9.90,1,1,NULL,'2026-05-03 16:52:49','2026-05-03 16:52:49',NULL,0),
(26,'ORD2026050300000040','TXN1777798578361ORD2026050300000040',NULL,9.90,1,1,NULL,'2026-05-03 16:56:18','2026-05-03 16:56:18',NULL,0),
(27,'ORD2026050300000041','TXN1777798988572ORD2026050300000041',NULL,9.90,1,1,NULL,'2026-05-03 17:03:09','2026-05-03 17:03:09',NULL,0),
(28,'ORD2026050300000042','TXN1777799459945ORD2026050300000042',NULL,9.90,1,1,NULL,'2026-05-03 17:11:00','2026-05-03 17:11:00',NULL,0),
(29,'ORD2026050300000043','TXN1777799790228ORD2026050300000043',NULL,9.90,1,1,NULL,'2026-05-03 17:16:30','2026-05-03 17:16:30',NULL,0),
(30,'ORD2026050300000044','TXN1777800080269ORD2026050300000044',NULL,9.90,1,1,NULL,'2026-05-03 17:21:20','2026-05-03 17:21:20',NULL,0),
(31,'ORD2026050300000045','TXN1777800360485ORD2026050300000045',NULL,9.90,1,1,NULL,'2026-05-03 17:26:00','2026-05-03 17:26:00',NULL,0);

/*Table structure for table `t_transaction_message` */

DROP TABLE IF EXISTS `t_transaction_message`;

CREATE TABLE `t_transaction_message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` varchar(64) NOT NULL COMMENT '消息ID（UUID）',
  `topic` varchar(64) NOT NULL COMMENT 'MQ Topic',
  `tag` varchar(32) DEFAULT '' COMMENT 'MQ Tag',
  `message_body` text NOT NULL COMMENT '消息内容（JSON）',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态：0-待发送 1-已发送 2-已确认 3-失败',
  `retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '重试次数',
  `max_retry_count` int(11) NOT NULL DEFAULT '3' COMMENT '最大重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_status_retry` (`status`,`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务消息表';

/*Data for the table `t_transaction_message` */

/*Table structure for table `undo_log` */

DROP TABLE IF EXISTS `undo_log`;

CREATE TABLE `undo_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `branch_id` bigint(20) NOT NULL,
  `xid` varchar(100) NOT NULL,
  `context` varchar(128) NOT NULL,
  `rollback_info` longblob NOT NULL,
  `log_status` int(11) NOT NULL,
  `log_created` datetime NOT NULL,
  `log_modified` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/*Data for the table `undo_log` */

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
