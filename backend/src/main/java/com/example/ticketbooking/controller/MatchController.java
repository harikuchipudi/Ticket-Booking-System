package com.example.ticketbooking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    public record Match(String id, String name, String team1, String team2, String venue, String date, String status) {}

    @GetMapping
    public List<Match> getMatches() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy • HH:mm");
        LocalDateTime now = LocalDateTime.now();

        return List.of(
            new Match("MI-vs-CSK", "MI vs CSK", "Mumbai Indians", "Chennai Super Kings", "Wankhede Stadium, Mumbai", now.plusDays(1).format(formatter), "booking_open"),
            new Match("RCB-vs-KKR", "RCB vs KKR", "Royal Challengers Bengaluru", "Kolkata Knight Riders", "M. Chinnaswamy Stadium, Bengaluru", now.plusDays(3).format(formatter), "booking_open"),
            new Match("GT-vs-RR", "GT vs RR", "Gujarat Titans", "Rajasthan Royals", "Narendra Modi Stadium, Ahmedabad", now.plusDays(5).format(formatter), "booking_open"),
            new Match("SRH-vs-DC", "SRH vs DC", "Sunrisers Hyderabad", "Delhi Capitals", "Rajiv Gandhi Intl Stadium, Hyderabad", now.plusDays(7).format(formatter), "booking_open"),
            new Match("LSG-vs-PBKS", "LSG vs PBKS", "Lucknow Super Giants", "Punjab Kings", "Ekana Cricket Stadium, Lucknow", now.plusDays(10).format(formatter), "booking_open")
        );
    }
}
