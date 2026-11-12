package com.fitlink.schedule.model;

import java.time.LocalTime;

public record Recurrence(long id, Weekday weekday, LocalTime startTime, int capacity, boolean active) {
}
