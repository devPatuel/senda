package dev.jordi.senda.habit;

import java.util.List;

public record HabitHistoryResponse(HabitResponse habit, List<HabitEntryResponse> entries,
                                   int currentStreak, int bestStreak, int completionRate) { }
