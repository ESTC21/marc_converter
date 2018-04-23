CREATE DATABASE  IF NOT EXISTS `estcimport_dev` /*!40100 DEFAULT CHARACTER SET utf8 COLLATE utf8_unicode_ci */;
USE `estcimport_dev`;
-- MySQL dump 10.13  Distrib 5.6.19, for osx10.7 (i386)
--
-- Host: localhost    Database: estcimport_dev
-- ------------------------------------------------------
-- Server version	5.6.21

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `estc_agents`
--

DROP TABLE IF EXISTS `estc_agents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `estc_agents` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `local_label` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `local_label` (`local_label`)
) ENGINE=InnoDB AUTO_INCREMENT=3511 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `estc_subject_terms`
--

DROP TABLE IF EXISTS `estc_subject_terms`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `estc_subject_terms` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `term` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `term` (`term`)
) ENGINE=InnoDB AUTO_INCREMENT=1476 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `field_types`
--

DROP TABLE IF EXISTS `field_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `field_types` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` varchar(45) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `fields_has_subfields`
--

DROP TABLE IF EXISTS `fields_has_subfields`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `fields_has_subfields` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `field_id` bigint(20) NOT NULL,
  `subfield` varchar(45) COLLATE utf8_unicode_ci NOT NULL,
  `value` text COLLATE utf8_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `field_id_index` (`field_id`)
) ENGINE=InnoDB AUTO_INCREMENT=41915834 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file_type_suffixes`
--

DROP TABLE IF EXISTS `file_type_suffixes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `file_type_suffixes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `file_type_id` int(11) NOT NULL,
  `suffix` varchar(45) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `file_type_id` (`file_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file_types`
--

DROP TABLE IF EXISTS `file_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `file_types` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'unique id',
  `type` varchar(15) COLLATE utf8_unicode_ci NOT NULL COMMENT 'file type (MARC, CSV, etc.)',
  PRIMARY KEY (`id`),
  KEY `type_index` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `files`
--

DROP TABLE IF EXISTS `files`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `files` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'unique id',
  `institution_code` varchar(10) COLLATE utf8_unicode_ci NOT NULL COMMENT 'institutional identifier code',
  `filename` varchar(100) COLLATE utf8_unicode_ci NOT NULL COMMENT 'name of the file',
  `modification_date` varchar(25) COLLATE utf8_unicode_ci NOT NULL COMMENT 'the unix file last mod timestamp',
  `type` int(11) NOT NULL COMMENT 'the type of file (MARC, csv, etc.)',
  `processed_successfully` tinyint(4) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `combined_index` (`id`,`institution_code`,`filename`,`modification_date`),
  KEY `filename_index` (`filename`),
  KEY `institution_code_index` (`institution_code`),
  KEY `modification_date` (`modification_date`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `record_types`
--

DROP TABLE IF EXISTS `record_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `record_types` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'unique id',
  `type` varchar(15) COLLATE utf8_unicode_ci NOT NULL COMMENT 'type (holding, bib, control, etc.)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `records`
--

DROP TABLE IF EXISTS `records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `records` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'unique id',
  `file_id` bigint(20) NOT NULL COMMENT 'files.id of associated file',
  `control_identifier` varchar(100) COLLATE utf8_unicode_ci NOT NULL COMMENT 'control id from record',
  `type` tinyint(4) NOT NULL COMMENT 'record_types.id',
  `scoped` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Identifies whether record has been tested for scope',
  `processed` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'indicates whether all record cleaning has been performed and the record is ready to export',
  `exported` tinyint(4) NOT NULL DEFAULT '0',
  `moddate` decimal(17,1) NOT NULL DEFAULT '0.0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `files_id` (`file_id`),
  KEY `control_identifier_index` (`control_identifier`),
  KEY `type` (`type`),
  KEY `scoped` (`scoped`),
  KEY `processed_index` (`processed`)
) ENGINE=InnoDB AUTO_INCREMENT=965346 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `records_has_fields`
--

DROP TABLE IF EXISTS `records_has_fields`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `records_has_fields` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'unique id',
  `record_id` bigint(20) NOT NULL COMMENT 'record.id',
  `field` char(3) COLLATE utf8_unicode_ci NOT NULL COMMENT 'MARC field',
  `value` text COLLATE utf8_unicode_ci NOT NULL COMMENT 'value stored in MARC field',
  `type` int(11) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `shortval` char(15) COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `record_id_index` (`record_id`),
  KEY `selectHoldingRecordIDs` (`field`,`shortval`)
) ENGINE=InnoDB AUTO_INCREMENT=15395789 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `records_has_images`
--

DROP TABLE IF EXISTS `records_has_images`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `records_has_images` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `records_id` bigint(20) DEFAULT NULL,
  `image_full_path` varchar(45) COLLATE utf8_unicode_ci DEFAULT NULL,
  `image_old_path` blob,
  `image_thumbnail` blob,
  `image_filename` varchar(45) COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `record_id` (`records_id`)
) ENGINE=InnoDB AUTO_INCREMENT=180332 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `recycled_field_types`
--

DROP TABLE IF EXISTS `recycled_field_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `recycled_field_types` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` varchar(45) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `recycled_fields`
--

DROP TABLE IF EXISTS `recycled_fields`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `recycled_fields` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` bigint(20) NOT NULL,
  `foreign_key` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `type` (`type`,`foreign_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `runlog`
--

DROP TABLE IF EXISTS `runlog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `runlog` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` int(11) NOT NULL,
  `file` varchar(45) COLLATE utf8_unicode_ci NOT NULL,
  `line` int(11) NOT NULL,
  `message` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=59242909 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `runlog_message_types`
--

DROP TABLE IF EXISTS `runlog_message_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `runlog_message_types` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `type` varchar(45) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2018-04-23  8:13:53
