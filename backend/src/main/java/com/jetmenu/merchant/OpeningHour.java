package com.jetmenu.merchant;

import lombok.*;

import java.time.DayOfWeek;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpeningHour {

    private DayOfWeek dayOfWeek;

    /** Hora de abertura no formato "HH:mm". Null quando {@link #closed} é true. */
    private String openTime;

    /** Hora de fechamento no formato "HH:mm". Null quando {@link #closed} é true. */
    private String closeTime;

    private boolean closed;
}
