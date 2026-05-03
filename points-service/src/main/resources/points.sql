/*
SQLyog Ultimate v13.1.1 (64 bit)
MySQL - 5.6.51-log : Database - points_db
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`points_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 */;

USE `points_db`;

/*Table structure for table `points_account` */

DROP TABLE IF EXISTS `points_account`;

CREATE TABLE `points_account` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `total_points` int(11) NOT NULL DEFAULT '0' COMMENT '总积分(累计获得的积分)',
  `available_points` int(11) NOT NULL DEFAULT '0' COMMENT '可用积分(可以用于消费的积分)',
  `frozen_points` int(11) NOT NULL DEFAULT '0' COMMENT '冻结积分(暂时不可用的积分)',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`) COMMENT '用户ID唯一索引',
  KEY `idx_created_time` (`created_time`) COMMENT '创建时间索引'
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COMMENT='积分账户表';

/*Data for the table `points_account` */

insert  into `points_account`(`id`,`user_id`,`total_points`,`available_points`,`frozen_points`,`created_time`,`updated_time`) values 
(1,1,9,9,0,'2026-05-03 16:41:10','2026-05-03 17:26:01');

/*Table structure for table `t_points_record` */

DROP TABLE IF EXISTS `t_points_record`;

CREATE TABLE `t_points_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `points` int(11) NOT NULL COMMENT '积分数量',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4;

/*Data for the table `t_points_record` */

insert  into `t_points_record`(`id`,`user_id`,`order_no`,`points`,`create_time`) values 
(1,1,'ORD2026050300000045',9,'2026-05-03 17:26:01');

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
