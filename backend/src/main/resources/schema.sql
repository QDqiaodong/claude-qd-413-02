SET NAMES utf8mb4;

DROP TABLE IF EXISTS master_release_item;
DROP TABLE IF EXISTS master_release;
DROP TABLE IF EXISTS track;
DROP TABLE IF EXISTS booking;
DROP TABLE IF EXISTS equipment;
DROP TABLE IF EXISTS room;
DROP TABLE IF EXISTS studio_setting;

CREATE TABLE room (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  power INT NULL,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE equipment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  room_id BIGINT NULL,
  type VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_equip_room FOREIGN KEY (room_id) REFERENCES room(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE booking (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_id BIGINT NOT NULL,
  equipment_id BIGINT NULL,
  cust_name VARCHAR(64) NOT NULL,
  start_min INT NOT NULL,
  end_min INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  version BIGINT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_booking_room FOREIGN KEY (room_id) REFERENCES room(id),
  CONSTRAINT fk_booking_equip FOREIGN KEY (equipment_id) REFERENCES equipment(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE track (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  duration INT NOT NULL,
  rating INT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_track_booking FOREIGN KEY (booking_id) REFERENCES booking(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 全棚级开关项：power_limit = 夜班电工定的全棚功率合计上限（W）
CREATE TABLE studio_setting (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  skey VARCHAR(64) NOT NULL UNIQUE,
  svalue VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 母带放行单：待齐件 -> 会签中 -> 已放行，只能向前、不可跳步/倒回
CREATE TABLE master_release (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  release_no VARCHAR(32) NOT NULL UNIQUE,
  booking_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  fail_reason VARCHAR(512) NULL,
  version BIGINT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  CONSTRAINT fk_release_booking FOREIGN KEY (booking_id) REFERENCES booking(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 放行单勾入的齐件曲目（勾选时必须有星级）
CREATE TABLE master_release_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  release_id BIGINT NOT NULL,
  track_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_item_release FOREIGN KEY (release_id) REFERENCES master_release(id),
  CONSTRAINT fk_item_track FOREIGN KEY (track_id) REFERENCES track(id),
  UNIQUE KEY uk_release_track (release_id, track_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO studio_setting (skey, svalue, created_at) VALUES
  ('power_limit', '7000', '2026-09-19 09:00:00');

INSERT INTO room (id, code, name, status, power, created_at) VALUES
  (1, 'R001', '主录音棚', '占用', 5000, '2026-09-19 09:00:00'),
  (2, 'R002', '人声室',   '空闲', 1500, '2026-09-19 09:00:00'),
  (3, 'R003', '鼓房',     '维护', 3000, '2026-09-19 09:00:00'),
  (4, 'R004', '母带室',   '空闲', 2000, '2026-09-19 09:00:00'),
  (5, 'R005', '排练室',   '占用', 1200, '2026-09-19 09:00:00');

INSERT INTO equipment (id, code, name, room_id, type, status, created_at) VALUES
  (1, 'E001', '主电容麦克风', 1, '麦克风', '占用', '2026-09-19 09:00:00'),
  (2, 'E002', '主控调音台',   1, '调音台', '空闲', '2026-09-19 09:00:00'),
  (3, 'E003', '近场监听箱',   2, '监听',   '空闲', '2026-09-19 09:00:00'),
  (4, 'E004', '鼓组麦克风',   3, '麦克风', '故障', '2026-09-19 09:00:00'),
  (5, 'E005', '动圈麦克风',   2, '麦克风', '占用', '2026-09-19 09:00:00'),
  (6, 'E006', '母带调音台',   4, '调音台', '空闲', '2026-09-19 09:00:00'),
  (7, 'E007', '排练监听箱',   5, '监听',   '占用', '2026-09-19 09:00:00');

INSERT INTO booking (id, room_id, equipment_id, cust_name, start_min, end_min, status, version, created_at) VALUES
  (1, 1, 1, '张三', 600, 660, '进行中', 0, '2026-09-19 09:00:00'),
  (2, 2, NULL, '李四', 600, 660, '待确认', 0, '2026-09-19 09:00:00'),
  (3, 3, 4, '王五', 720, 780, '待确认', 0, '2026-09-19 09:00:00'),
  (4, 4, 6, '赵六', 600, 660, '已完成', 0, '2026-09-19 09:00:00'),
  (5, 5, 7, '孙七', 800, 860, '进行中', 0, '2026-09-19 09:00:00'),
  (6, 3, NULL, '周九', 900, 960, '已完成', 0, '2026-09-19 09:00:00');

INSERT INTO track (id, booking_id, name, duration, rating, created_at) VALUES
  (1, 1, '晚风',   4, 5,   '2026-09-19 09:00:00'),
  (2, 1, '夜空',   3, 4,   '2026-09-19 09:00:00'),
  (3, 4, '晨雾',   5, 5,   '2026-09-19 09:00:00'),
  (4, 5, '潮汐',   6, 3,   '2026-09-19 09:00:00'),
  (5, 3, '山谷',   4, 2,   '2026-09-19 09:00:00'),
  (6, 2, '城市',   3, NULL,'2026-09-19 09:00:00'),
  (7, 4, '微光',   4, 4,   '2026-09-19 09:00:00'),
  (8, 4, '归途',   3, NULL,'2026-09-19 09:00:00'),
  (9, 6, '旧巷',   5, 4,   '2026-09-19 09:00:00'),
  (10, 6, '远行',  4, 5,   '2026-09-19 09:00:00');
