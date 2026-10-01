/*M!999999\- enable the sandbox mode */ 

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*M!100616 SET @OLD_NOTE_VERBOSITY=@@NOTE_VERBOSITY, NOTE_VERBOSITY=0 */;
DROP TABLE IF EXISTS `appointment_slots`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `appointment_slots` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `end_time` time NOT NULL,
  `is_available` bit(1) NOT NULL,
  `note` varchar(255) DEFAULT NULL,
  `slot_date` date NOT NULL,
  `start_time` time NOT NULL,
  `status` varchar(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint(20) NOT NULL,
  `doctor_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKndehqtdq1a94ja5ej6xs482kd` (`doctor_id`),
  CONSTRAINT `FKndehqtdq1a94ja5ej6xs482kd` FOREIGN KEY (`doctor_id`) REFERENCES `doctors` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `appointments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `appointments` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `notes` text DEFAULT NULL,
  `patient_id` bigint(20) NOT NULL,
  `status` enum('CANCELLED','COMPLETED','CONFIRMED','IN_PROGRESS','PENDING') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `doctor_id` bigint(20) NOT NULL,
  `slot_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8y6yin1cflvk14414e91mdcwm` (`slot_id`),
  KEY `FKmujeo4tymoo98cmf7uj3vsv76` (`doctor_id`),
  KEY `fk_appointments_patient` (`patient_id`),
  CONSTRAINT `FKmujeo4tymoo98cmf7uj3vsv76` FOREIGN KEY (`doctor_id`) REFERENCES `doctors` (`id`),
  CONSTRAINT `FKmyaw0q6vm88nedh13msfjtvry` FOREIGN KEY (`slot_id`) REFERENCES `appointment_slots` (`id`),
  CONSTRAINT `fk_appointments_patient` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `attachments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `attachments` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `content_type` varchar(120) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_size_bytes` bigint(20) DEFAULT NULL,
  `file_url` varchar(500) NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `owner_type` varchar(80) NOT NULL,
  `status` varchar(20) NOT NULL,
  `uploaded_by_user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKn1dtrr2r8fqwkcj2qjspy5wq6` (`uploaded_by_user_id`),
  CONSTRAINT `FKn1dtrr2r8fqwkcj2qjspy5wq6` FOREIGN KEY (`uploaded_by_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `action_code` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `entity_id` bigint(20) DEFAULT NULL,
  `entity_type` varchar(80) NOT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `metadata_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`metadata_json`)),
  `user_agent` varchar(500) DEFAULT NULL,
  `actor_user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK17vn8rhj6qver0naebk935vkk` (`actor_user_id`),
  CONSTRAINT `FK17vn8rhj6qver0naebk935vkk` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `doctor_schedules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `doctor_schedules` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `end_time` time NOT NULL,
  `start_time` time NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `work_date` date NOT NULL,
  `doctor_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqptts4sun4tpv6elafrnrfeup` (`doctor_id`),
  CONSTRAINT `FKqptts4sun4tpv6elafrnrfeup` FOREIGN KEY (`doctor_id`) REFERENCES `doctors` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `doctors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `doctors` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `bio` text DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `license_number` varchar(50) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `specialty_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt1f6cueqyjwx5ghew9ar1exe3` (`user_id`),
  KEY `FKb4ymcpidvwfn4kybv4adfvxcm` (`specialty_id`),
  CONSTRAINT `FKb4ymcpidvwfn4kybv4adfvxcm` FOREIGN KEY (`specialty_id`) REFERENCES `specialties` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `encounters`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `encounters` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `chief_complaint` tinytext DEFAULT NULL,
  `clinical_notes` tinytext DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `diagnosis` longtext DEFAULT NULL,
  `encounter_at` datetime(6) NOT NULL,
  `follow_up_note` tinytext DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `treatment_plan` tinytext DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `appointment_id` bigint(20) DEFAULT NULL,
  `doctor_id` bigint(20) NOT NULL,
  `medical_record_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7mb39tis8bpvbjeof3nhs3rx1` (`appointment_id`),
  KEY `FKmgx7gtqfllaa3scxg5k9e2y60` (`doctor_id`),
  KEY `FK5ru45bdb7qrudgv6xc8phycmo` (`medical_record_id`),
  CONSTRAINT `FK40145xu8iu6aevo4wp9durw5x` FOREIGN KEY (`appointment_id`) REFERENCES `appointments` (`id`),
  CONSTRAINT `FK5ru45bdb7qrudgv6xc8phycmo` FOREIGN KEY (`medical_record_id`) REFERENCES `medical_records` (`id`),
  CONSTRAINT `FKmgx7gtqfllaa3scxg5k9e2y60` FOREIGN KEY (`doctor_id`) REFERENCES `doctors` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `description` varchar(255) NOT NULL,
  `line_total` decimal(12,2) NOT NULL,
  `quantity` decimal(10,2) NOT NULL,
  `unit_price` decimal(12,2) NOT NULL,
  `invoice_id` bigint(20) NOT NULL,
  `service_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK46ae0lhu1oqs7cv91fn6y9n7w` (`invoice_id`),
  KEY `FK8ri0hkud88yc7fyj8g2sxlail` (`service_id`),
  CONSTRAINT `FK46ae0lhu1oqs7cv91fn6y9n7w` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`),
  CONSTRAINT `FK8ri0hkud88yc7fyj8g2sxlail` FOREIGN KEY (`service_id`) REFERENCES `services` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoices` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `discount_amount` decimal(12,2) NOT NULL,
  `invoice_code` varchar(40) NOT NULL,
  `issued_at` datetime(6) DEFAULT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `subtotal` decimal(12,2) NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `appointment_id` bigint(20) DEFAULT NULL,
  `patient_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9yfpp5o7v861ie3702mnhm3hv` (`invoice_code`),
  UNIQUE KEY `UKr1gsksmeq3yb5fipnxl93yqqv` (`appointment_id`),
  KEY `FKrpyotno5h237hyoaokuggqqog` (`patient_id`),
  CONSTRAINT `FKngg5bc8atao2b9jehl9l8tdsw` FOREIGN KEY (`appointment_id`) REFERENCES `appointments` (`id`),
  CONSTRAINT `FKrpyotno5h237hyoaokuggqqog` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `medical_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `medical_records` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `allergy_notes` tinytext DEFAULT NULL,
  `blood_type` varchar(5) DEFAULT NULL,
  `chronic_conditions` tinytext DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `current_medications` tinytext DEFAULT NULL,
  `medical_history` tinytext DEFAULT NULL,
  `record_code` varchar(40) NOT NULL,
  `status` varchar(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `patient_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsg0da4os8s4phrfsi6vmlqyls` (`record_code`),
  UNIQUE KEY `UKpfo95i6n9sje9q9mb429f5qor` (`patient_id`),
  CONSTRAINT `FKrav12h9aiw7pegjt62p8owwn3` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `medicines`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `medicines` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `name` varchar(200) NOT NULL,
  `status` varchar(20) NOT NULL,
  `unit` varchar(50) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2eauinawkcwc1dgy1xkmkpog5` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `patients`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `patients` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `address` varchar(255) DEFAULT NULL,
  `allergies` longtext DEFAULT NULL,
  `blood_type` varchar(5) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `date_of_birth` date NOT NULL,
  `email` varchar(190) DEFAULT NULL,
  `emergency_contact_name` varchar(150) DEFAULT NULL,
  `emergency_contact_phone` varchar(30) DEFAULT NULL,
  `full_name` varchar(150) NOT NULL,
  `gender_code` varchar(20) NOT NULL,
  `patient_code` varchar(30) NOT NULL,
  `phone` varchar(30) NOT NULL,
  `status` varchar(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpdu5f0e015icwwcx7otn46rv8` (`patient_code`),
  UNIQUE KEY `UK9tbsl3fmey0eofbm2xj69v4qs` (`user_id`),
  CONSTRAINT `FKuwca24wcd1tg6pjex8lmc0y7` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `prescription_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `prescription_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `dosage` varchar(100) DEFAULT NULL,
  `duration_days` int(11) DEFAULT NULL,
  `frequency` varchar(100) DEFAULT NULL,
  `instructions` varchar(500) DEFAULT NULL,
  `medicine_name` varchar(200) NOT NULL,
  `quantity` decimal(10,2) NOT NULL,
  `prescription_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6uh7tdy2lv6sx34u1365acqsf` (`prescription_id`),
  CONSTRAINT `FK6uh7tdy2lv6sx34u1365acqsf` FOREIGN KEY (`prescription_id`) REFERENCES `prescriptions` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `prescriptions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `prescriptions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `issued_at` datetime(6) NOT NULL,
  `notes` varchar(500) DEFAULT NULL,
  `prescription_code` varchar(40) NOT NULL,
  `status` varchar(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `encounter_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpelkfgbey41qomugpv5ax7dld` (`prescription_code`),
  KEY `FK5brauc91c7omvqhemvtly1sop` (`encounter_id`),
  CONSTRAINT `FK5brauc91c7omvqhemvtly1sop` FOREIGN KEY (`encounter_id`) REFERENCES `encounters` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKch1113horj4qr56f91omojv8` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `schedule_breaks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule_breaks` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `end_time` time NOT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `start_time` time NOT NULL,
  `doctor_schedule_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK437iw7sgf0soowkyk0usgssov` (`doctor_schedule_id`),
  CONSTRAINT `FK437iw7sgf0soowkyk0usgssov` FOREIGN KEY (`doctor_schedule_id`) REFERENCES `doctor_schedules` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `services`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `services` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(30) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `duration_minutes` int(11) NOT NULL,
  `name` varchar(150) NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `status` varchar(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3ff0vue74scb6a5dbic1yj2ip` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `specialties`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `specialties` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `name` varchar(120) NOT NULL,
  `status` varchar(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8hq46e4ylgbhk67ke0u9y52i3` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_roles` (
  `created_at` datetime(6) NOT NULL,
  `role_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`role_id`,`user_id`),
  KEY `FKhfh9dx7w3ubf1co1vdev94g3f` (`user_id`),
  CONSTRAINT `FKh8ciramu9cc9q3qcqiv4ue8a6` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `FKhfh9dx7w3ubf1co1vdev94g3f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(190) DEFAULT NULL,
  `full_name` varchar(150) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `username` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*M!100616 SET NOTE_VERBOSITY=@OLD_NOTE_VERBOSITY */;

