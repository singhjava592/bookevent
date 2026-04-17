CREATE TABLE events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  event_date DATETIME NOT NULL,
  location VARCHAR(255) NOT NULL,
  total_seats INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  deleted_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  CONSTRAINT chk_events_total_seats CHECK (total_seats >= 0)
);

CREATE INDEX idx_events_status ON events(status);

CREATE TABLE seat_holds (
  id CHAR(36) PRIMARY KEY,
  event_id BIGINT NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  quantity INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  confirmed_booking_id BIGINT NULL,
  CONSTRAINT chk_holds_qty CHECK (quantity > 0),
  CONSTRAINT fk_hold_event FOREIGN KEY (event_id) REFERENCES events(id)
);

CREATE INDEX idx_holds_event_status_exp ON seat_holds(event_id, status, expires_at);
CREATE INDEX idx_holds_user_event_status ON seat_holds(user_id, event_id, status);

CREATE TABLE bookings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  quantity INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  canceled_at DATETIME NULL,
  CONSTRAINT chk_bookings_qty CHECK (quantity > 0),
  CONSTRAINT fk_booking_event FOREIGN KEY (event_id) REFERENCES events(id)
);

CREATE INDEX idx_bookings_event_status ON bookings(event_id, status);
CREATE INDEX idx_bookings_user_event_status ON bookings(user_id, event_id, status);
