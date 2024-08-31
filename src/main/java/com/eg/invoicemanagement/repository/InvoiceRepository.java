package com.eg.invoicemanagement.repository;

import com.eg.invoicemanagement.model.Invoice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {

    @Query("select invoice from Invoice invoice where status = 'PENDING' and paidAmount < amount and " +
            "dueDate < curdate()")
    List<Invoice> findPendingOverdueInvoices();
}
