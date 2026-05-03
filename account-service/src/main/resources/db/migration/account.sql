/*
SQLyog Ultimate v13.1.1 (64 bit)
MySQL - 5.6.51-log : Database - account_db
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`account_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 */;

USE `account_db`;

/*Table structure for table `t_account` */

DROP TABLE IF EXISTS `t_account`;

CREATE TABLE `t_account` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `balance` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '账户余额',
  `frozen` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '冻结金额',
  `version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;

/*Data for the table `t_account` */

insert  into `t_account`(`id`,`user_id`,`balance`,`frozen`,`version`) values 
(1,1,3342.23,0.00,0);

/*Table structure for table `t_account_frozen_log` */

DROP TABLE IF EXISTS `t_account_frozen_log`;

CREATE TABLE `t_account_frozen_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `frozen_amount` decimal(10,2) NOT NULL COMMENT '冻结金额',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态: 0-已冻结, 1-已解冻, 2-已扣款',
  `frozen_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '冻结时间',
  `unfrozen_time` datetime DEFAULT NULL COMMENT '解冻时间',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`) COMMENT '订单号唯一索引(防止重复冻结)',
  KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引',
  KEY `idx_status` (`status`) COMMENT '状态索引',
  KEY `idx_frozen_time` (`frozen_time`) COMMENT '时间索引'
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COMMENT='账户冻结记录表';

/*Data for the table `t_account_frozen_log` */

insert  into `t_account_frozen_log`(`id`,`order_no`,`user_id`,`frozen_amount`,`status`,`frozen_time`,`unfrozen_time`,`created_time`,`updated_time`) values 
(1,'ORD2026050300000036',1,9.90,0,'2026-05-03 16:39:32',NULL,'2026-05-03 16:39:32','2026-05-03 16:39:32'),
(2,'ORD2026050300000037',1,9.90,0,'2026-05-03 16:44:54',NULL,'2026-05-03 16:44:54','2026-05-03 16:44:54'),
(3,'ORD2026050300000038',1,9.90,0,'2026-05-03 16:50:20',NULL,'2026-05-03 16:50:20','2026-05-03 16:50:20'),
(4,'ORD2026050300000039',1,9.90,0,'2026-05-03 16:52:49',NULL,'2026-05-03 16:52:49','2026-05-03 16:52:49'),
(5,'ORD2026050300000040',1,9.90,0,'2026-05-03 16:56:18',NULL,'2026-05-03 16:56:18','2026-05-03 16:56:18'),
(6,'ORD2026050300000041',1,9.90,0,'2026-05-03 17:03:09',NULL,'2026-05-03 17:03:09','2026-05-03 17:03:09'),
(7,'ORD2026050300000042',1,9.90,0,'2026-05-03 17:11:00',NULL,'2026-05-03 17:11:00','2026-05-03 17:11:00'),
(8,'ORD2026050300000043',1,9.90,0,'2026-05-03 17:16:30',NULL,'2026-05-03 17:16:30','2026-05-03 17:16:30'),
(9,'ORD2026050300000044',1,9.90,0,'2026-05-03 17:21:20',NULL,'2026-05-03 17:21:20','2026-05-03 17:21:20'),
(10,'ORD2026050300000045',1,9.90,0,'2026-05-03 17:26:00',NULL,'2026-05-03 17:26:00','2026-05-03 17:26:00');

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
