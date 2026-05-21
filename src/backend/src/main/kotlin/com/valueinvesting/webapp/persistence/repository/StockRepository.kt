package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.Stock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

// Catalog of known tickers.  Upserts are handled at the service layer via
// existsById + save (no native ON CONFLICT needed — single-row contention here
// is minimal and Spring Data does the right thing for first-write-wins).
// [^src: design_&_architecture/components/backend-components.md §persistence]
@Repository
interface StockRepository : JpaRepository<Stock, String>
