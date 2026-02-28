package np.edu.nast.payroll.Payroll.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import np.edu.nast.payroll.Payroll.entity.Holiday;
import np.edu.nast.payroll.Payroll.repository.HolidayRepository;
import np.edu.nast.payroll.Payroll.service.HolidayService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayServiceImpl implements HolidayService {

    private final HolidayRepository holidayRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void syncNationalHolidays(int year) {
        String url = "https://date.nager.at/api/v3/PublicHolidays/" + year + "/NP";
        try {
            HolidayResponse[] apiHolidays = restTemplate.getForObject(url, HolidayResponse[].class);
            if (apiHolidays != null) {
                for (HolidayResponse res : apiHolidays) {
                    LocalDate holidayDate = LocalDate.parse(res.getDate());
                    if (!holidayRepository.existsByHolidayDate(holidayDate)) {
                        Holiday h = new Holiday();
                        h.setHolidayDate(holidayDate);

                        // Map API names to holidayName
                        String name = res.getLocalName() != null ? res.getLocalName() : res.getName();
                        h.setHolidayName(name);

                        // Description is now null
                        h.setDescription(null);
                        h.setHolidayType("NATIONAL");
                        holidayRepository.save(h);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Sync error: {}", e.getMessage());
            throw new RuntimeException("Failed to sync Nepal holidays: " + e.getMessage());
        }
    }

    @Override
    public void generateSaturdaysForMonth(int year, int month) {
        LocalDate date = LocalDate.of(year, month, 1);
        while (date.getMonthValue() == month) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
                if (!holidayRepository.existsByHolidayDate(date)) {
                    Holiday sat = new Holiday();
                    sat.setHolidayDate(date);
                    sat.setHolidayName("Saturday");
                    sat.setDescription(null); // Description set to null
                    sat.setHolidayType("WEEKEND");
                    holidayRepository.save(sat);
                }
            }
            date = date.plusDays(1);
        }
    }

    @Override
    public Holiday saveHoliday(Holiday holiday) {
        if (holiday.getHolidayDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot create a holiday for a past date.");
        }
        if (holidayRepository.existsByHolidayDate(holiday.getHolidayDate())) {
            throw new RuntimeException("A holiday is already set for " + holiday.getHolidayDate());
        }

        // Use holidayName from request, fallback if null
        if (holiday.getHolidayName() == null || holiday.getHolidayName().isEmpty()) {
            holiday.setHolidayName("Custom Holiday");
        }

        // Ensure description is not saved
        holiday.setDescription(null);

        return holidayRepository.save(holiday);
    }

    @Override
    public void saveHolidayRange(LocalDate start, LocalDate end, String holidayName, String type) {
        if (start.isBefore(LocalDate.now())) throw new IllegalArgumentException("Start date cannot be in the past.");
        if (end.isBefore(start)) throw new IllegalArgumentException("End date must be after start date.");

        LocalDate current = start;
        while (!current.isAfter(end)) {
            // Check: Don't save if it's a Saturday OR if the holiday already exists
            boolean isSaturday = current.getDayOfWeek() == DayOfWeek.SATURDAY;

            if (!isSaturday && !holidayRepository.existsByHolidayDate(current)) {
                Holiday h = new Holiday();
                h.setHolidayDate(current);
                h.setHolidayName(holidayName); // Description param from controller maps to Name
                h.setDescription(null);        // Description is null
                h.setHolidayType(type != null ? type : "NATIONAL");
                holidayRepository.save(h);
            }
            current = current.plusDays(1);
        }
    }

    @Override
    public List<Holiday> getHolidaysInRange(LocalDate start, LocalDate end) {
        return holidayRepository.findByHolidayDateBetween(start, end);
    }

    @Override
    public void deleteHoliday(Long id) {
        holidayRepository.deleteById(id);
    }

    @Data
    private static class HolidayResponse {
        private String date;
        private String name;
        @JsonProperty("localName")
        private String localName;
    }
}