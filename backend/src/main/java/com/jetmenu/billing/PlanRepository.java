package com.jetmenu.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findByActiveTrueOrderByMinRevenueAsc();

    boolean existsByName(String name);

    Optional<Plan> findByName(String name);
}
