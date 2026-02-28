package np.edu.nast.payroll.Payroll.controller;

import lombok.RequiredArgsConstructor;
import np.edu.nast.payroll.Payroll.entity.Holiday;
import np.edu.nast.payroll.Payroll.service.HolidayService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class HolidayController {

    private final HolidayService holidayService;

    /**
     * API SYNC: Fetches Nepal National Holidays from Nager.Date API
     */
    @PostMapping("/sync/{year}")
    public ResponseEntity<?> syncNationalHolidays(@PathVariable int year) {
        try {
            holidayService.syncNationalHolidays(year);
            return ResponseEntity.ok(Map.of("message", "Successfully synchronized Nepal National Holidays for " + year));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Sync failed: " + e.getMessage()));
        }
    }

    /**
     * WEEKEND GENERATOR: Automatically marks all Saturdays in a month as holidays
     */
    @PostMapping("/generate-saturdays")
    public ResponseEntity<?> generateSaturdays(
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        try {
            holidayService.generateSaturdaysForMonth(year, month);
            return ResponseEntity.ok(Map.of("message", "Saturdays for " + year + "-" + month + " generated successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Generation failed: " + e.getMessage()));
        }
    }

    /**
     * SINGLE CREATE: Save a manual holiday
     * Frontend sends JSON body. The Service handles nulling the description.
     */
    @PostMapping
    public ResponseEntity<?> createHoliday(@RequestBody Holiday holiday) {
        try {
            Holiday saved = holidayService.saveHoliday(holiday);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * BULK RANGE: Supports multi-day selection
     * Note: We keep "description" as the param name to match your React 'axios.post' params,
     * but the Service will save this value into the 'holidayName' column.
     */
    @PostMapping("/bulk")
    public ResponseEntity<?> createHolidayRange(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam("description") String holidayName,
            @RequestParam(value = "type", defaultValue = "NATIONAL") String type) {
        try {
            // Pass the holidayName directly to the service
            holidayService.saveHolidayRange(start, end, holidayName, type);
            return ResponseEntity.ok(Map.of("message", "Holiday '" + holidayName + "' range created successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * FILTERED LIST: Used by the Calendar UI
     */
    @GetMapping
    public ResponseEntity<List<Holiday>> getHolidays(
            @RequestParam(name = "start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(name = "end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(holidayService.getHolidaysInRange(start, end));
    }

    /**
     * DELETE: Remove a specific holiday by ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeHoliday(@PathVariable Long id) {
        try {
            holidayService.deleteHoliday(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Holiday not found or could not be deleted."));
        }
    }
}