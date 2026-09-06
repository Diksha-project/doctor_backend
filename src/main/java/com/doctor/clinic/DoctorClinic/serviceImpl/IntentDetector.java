package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.doctor.clinic.DoctorClinic.model.Intent;

@Service
public class IntentDetector {
	
	public Intent detect(String message) {

        if (message == null || message.trim().isEmpty()) {
            return Intent.UNKNOWN;
        }

        String text = message
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z\\s]", "")
                .replaceAll("\\s+", " ");

        // GREETING
        if (text.matches("h+i+")
                || text.matches("h+e+l+l+o+")
                || text.matches("h+e+y+")
                || text.equals("good morning")
                || text.equals("good afternoon")
                || text.equals("good evening")) {

            return Intent.GREETING;
        }

        // THANKS
        if (text.equals("thanks")
                || text.equals("thank you")
                || text.equals("thankyou")
                || text.equals("thx")
                || text.equals("ty")) {

            return Intent.THANKS;
        }

        // GOODBYE
        if (text.equals("bye")
                || text.equals("goodbye")
                || text.equals("good night")
                || text.equals("goodnight")) {

            return Intent.GOODBYE;
        }

        return Intent.UNKNOWN;
    }
	
	

}
