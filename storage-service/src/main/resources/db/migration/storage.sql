/*
SQLyog Ultimate v13.1.1 (64 bit)
MySQL - 5.6.51-log : Database - storage_db
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`storage_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 */;

USE `storage_db`;

/*Table structure for table `t_storage` */

DROP TABLE IF EXISTS `t_storage`;

CREATE TABLE `t_storage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `total` int(11) NOT NULL COMMENT '总库存',
  `used` int(11) NOT NULL DEFAULT '0' COMMENT '已用库存',
  `residue` int(11) NOT NULL COMMENT '剩余可用库存',
  `version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_id` (`product_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;

/*Data for the table `t_storage` */

insert  into `t_storage`(`id`,`product_id`,`total`,`used`,`residue`,`version`) values 
(1,1,5000,2570,2430,0);

/*Table structure for table `t_storage_deduct_log` */

DROP TABLE IF EXISTS `t_storage_deduct_log`;

CREATE TABLE `t_storage_deduct_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `deduct_quantity` int(11) NOT NULL COMMENT '扣减数量',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态: 0-已扣减, 1-已恢复, 2-已发货',
  `deduct_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '扣减时间',
  `restore_time` datetime DEFAULT NULL COMMENT '恢复时间',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_product` (`order_no`,`product_id`) COMMENT '订单+商品唯一索引',
  KEY `idx_product_id` (`product_id`) COMMENT '商品ID索引',
  KEY `idx_status` (`status`) COMMENT '状态索引',
  KEY `idx_deduct_time` (`deduct_time`) COMMENT '时间索引'
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COMMENT='库存扣减记录表';

/*Data for the table `t_storage_deduct_log` */

insert  into `t_storage_deduct_log`(`id`,`order_no`,`product_id`,`deduct_quantity`,`status`,`deduct_time`,`restore_time`,`created_time`,`updated_time`) values 
(1,'ORD2026050300000036',1,2,0,'2026-05-03 16:39:22',NULL,'2026-05-03 16:39:22','2026-05-03 16:39:22'),
(2,'ORD2026050300000037',1,2,0,'2026-05-03 16:44:46',NULL,'2026-05-03 16:44:46','2026-05-03 16:44:46'),
(3,'ORD2026050300000038',1,2,0,'2026-05-03 16:50:13',NULL,'2026-05-03 16:50:13','2026-05-03 16:50:13'),
(4,'ORD2026050300000039',1,2,0,'2026-05-03 16:51:02',NULL,'2026-05-03 16:51:02','2026-05-03 16:51:02'),
(5,'ORD2026050300000040',1,2,0,'2026-05-03 16:56:07',NULL,'2026-05-03 16:56:07','2026-05-03 16:56:07'),
(6,'ORD2026050300000041',1,2,0,'2026-05-03 17:02:59',NULL,'2026-05-03 17:02:59','2026-05-03 17:02:59'),
(7,'ORD2026050300000042',1,2,0,'2026-05-03 17:10:53',NULL,'2026-05-03 17:10:53','2026-05-03 17:10:53'),
(8,'ORD2026050300000043',1,2,0,'2026-05-03 17:16:24',NULL,'2026-05-03 17:16:24','2026-05-03 17:16:24'),
(9,'ORD2026050300000044',1,2,0,'2026-05-03 17:21:12',NULL,'2026-05-03 17:21:12','2026-05-03 17:21:12'),
(10,'ORD2026050300000045',1,2,0,'2026-05-03 17:25:50',NULL,'2026-05-03 17:25:50','2026-05-03 17:25:50'),
(11,'ORD2026050300000046',1,2,0,'2026-05-03 17:27:09',NULL,'2026-05-03 17:27:09','2026-05-03 17:27:09'),
(12,'ORD2026050300000047',1,2,0,'2026-05-03 17:27:10',NULL,'2026-05-03 17:27:10','2026-05-03 17:27:10'),
(13,'ORD2026050300000048',1,2,0,'2026-05-03 17:27:12',NULL,'2026-05-03 17:27:12','2026-05-03 17:27:12'),
(14,'ORD2026050300000049',1,2,0,'2026-05-03 17:27:13',NULL,'2026-05-03 17:27:13','2026-05-03 17:27:13');

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
