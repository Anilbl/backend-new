package np.edu.nast.payroll.Payroll.repository;

import np.edu.nast.payroll.Payroll.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    // Check if a specific date is a holiday (Used for Attendance validation)
    Optional<Holiday> findByHolidayDate(LocalDate date);

    // Boolean check for quick validation
    boolean existsByHolidayDate(LocalDate date);

    @Query("SELECT COUNT(h) FROM Holiday h WHERE h.holidayDate BETWEEN :start AND :end")
    long countHolidaysInRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    // Crucial for payroll: Find holidays between two dates (month start and end)
    List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
}