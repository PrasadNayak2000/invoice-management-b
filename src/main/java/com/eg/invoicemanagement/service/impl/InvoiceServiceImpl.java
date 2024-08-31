package com.eg.invoicemanagement.service.impl;

import com.eg.invoicemanagement.dto.request.InvoiceCreationRequest;
import com.eg.invoicemanagement.dto.request.InvoicePaymentRequest;
import com.eg.invoicemanagement.dto.request.OverdueProcessRequest;
import com.eg.invoicemanagement.dto.response.InvoiceResponse;
import com.eg.invoicemanagement.model.Invoice;
import com.eg.invoicemanagement.model.enums.Status;
import com.eg.invoicemanagement.repository.InvoiceRepository;
import com.eg.invoicemanagement.service.InvoiceService;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;

    @Override
    public ResponseEntity<Object> createInvoice(InvoiceCreationRequest request) {
        Invoice invoice = new Invoice();
        invoice.setAmount(request.getAmount());
        invoice.setDueDate(request.getDueDate());
        invoice.setStatus(Status.PENDING);
        invoice = invoiceRepository.save(invoice);
        log.info("Invoice created with id {}", invoice.getId());
        return new ResponseEntity<>(new InvoiceResponse(invoice.getId()), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<Object> getAllInvoices() {
        List<Invoice> invoices = invoiceRepository.findAll();
        val response = invoices.stream().map(this::getResponse).toList();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private InvoiceResponse getResponse(Invoice invoice) {
        InvoiceResponse response = new InvoiceResponse();
        BeanUtils.copyProperties(invoice, response);
        response.setStatus(invoice.getStatus().getValue());
        return response;
    }

    @Override
    public ResponseEntity<Object> doPayment(Integer invoiceId, InvoicePaymentRequest request) {
        val invoiceOpt = invoiceRepository.findById(invoiceId);
        //Checking whether invoice exists with given invoiceId
        if (invoiceOpt.isEmpty())
            return new ResponseEntity<>("No invoice found with id " + invoiceId, HttpStatus.BAD_REQUEST);
        Invoice invoice = invoiceOpt.get();

        double invoiceAmount = invoice.getAmount();
        double paidAmount = invoice.getPaidAmount();
        double pendingAmount = invoiceAmount - paidAmount;
        double paymentAmount = request.getAmount();

        //Checking if payment amount exceeds the invoice pending amount
        if (paymentAmount > pendingAmount)
            return new ResponseEntity<>("Total Payment amount exceeds invoice amount " + invoiceAmount +
                    " (" + paidAmount + " paid already)", HttpStatus.BAD_REQUEST);

        invoice.setPaidAmount(paidAmount + paymentAmount);
        if (paymentAmount == pendingAmount)
            invoice.setStatus(Status.PAID);
        invoiceRepository.save(invoice);
        log.info("Invoice payment of {} for invoice id {} is successful", paymentAmount, invoiceId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    @Transactional
    public ResponseEntity<Object> processOverdue(OverdueProcessRequest request) {
        List<Invoice> pendingInvoices = invoiceRepository.findPendingOverdueInvoices();
        if (pendingInvoices.isEmpty())
            return new ResponseEntity<>(HttpStatus.OK);

        double lateFee = request.getLateFee();
        int overdueDays = request.getOverdueDays();
        long overdueInMilliSeconds = overdueDays * (1000L * 60 * 60 * 24); //Total milliseconds of a day 1000 * 60 * 60 *24

        //For storing newly created invoices
        List<Invoice> newInvoices = new ArrayList<>();

        //Update status of each invoice and create a new invoice for each
        for (Invoice invoice : pendingInvoices) {
            double amount = invoice.getAmount();
            double paidAmount = invoice.getPaidAmount();

            //If invoice is partially paid, mark status as 'paid' else mark as 'void' if not at all paid
            if (paidAmount > 0)
                invoice.setStatus(Status.PAID);
            else
                invoice.setStatus(Status.VOID);

            //Create a new invoice with amount (remaining amount + late fee) and a new overdue

            Invoice newInvoice = new Invoice();
            newInvoice.setAmount((amount - paidAmount) + lateFee);
            newInvoice.setDueDate(new Date(invoice.getDueDate().getTime() + overdueInMilliSeconds));
            newInvoice.setStatus(Status.PENDING);
            newInvoices.add(newInvoice);
        }
        //Save all pending overdue invoices whose status is updated
        invoiceRepository.saveAll(pendingInvoices);
        //Save all newly created invoices
        invoiceRepository.saveAll(newInvoices);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
