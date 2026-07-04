package com.trade.strategy.repository;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trade.strategy.entity.Inventory;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductCode(String productCode);
}

