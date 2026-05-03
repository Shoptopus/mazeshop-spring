package com.shoptopus.core.invoice;

import com.shoptopus.core.auth.AccessToken;
import com.shoptopus.core.auth.security.AccessTokenRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.invoice.repository.InvoiceRepository;
import com.shoptopus.core.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class InvoiceAccessService {

    public static final int INVOICE_ACCESS_TOKEN_TYPE = 4;
    public static final String INVOICE_ACCESSABLE_TYPE = "Invoice";

    private final AccessTokenRepository accessTokenRepository;
    private final InvoiceRepository invoiceRepository;

    public InvoiceAccessService(
            AccessTokenRepository accessTokenRepository,
            InvoiceRepository invoiceRepository
    ) {
        this.accessTokenRepository = accessTokenRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional("mainTransactionManager")
    public AccessToken issueInvoiceToken(Invoice invoice, User user) {
        AccessToken accessToken = new AccessToken();
        accessToken.setType(INVOICE_ACCESS_TOKEN_TYPE);
        accessToken.setToken(UUID.randomUUID().toString());
        accessToken.setAccessableType(INVOICE_ACCESSABLE_TYPE);
        accessToken.setAccessableId(invoice.getId().toString());
        accessToken.setUser(user);
        accessToken.setExpiry(OffsetDateTime.now().plusDays(30));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("invoice_id", invoice.getId().toString());
        content.put("invoice_slug", invoice.getSlug());
        content.put("order_id", invoice.getOrder() == null ? null : invoice.getOrder().getId().toString());
        accessToken.setContent(content);
        return accessTokenRepository.save(accessToken);
    }

    @Transactional("mainTransactionManager")
    public Invoice resolveInvoiceByToken(String token) {
        AccessToken accessToken = accessTokenRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid invoice link", "This invoice access token is invalid."));
        if (!Integer.valueOf(INVOICE_ACCESS_TOKEN_TYPE).equals(accessToken.getType())
                || !INVOICE_ACCESSABLE_TYPE.equals(accessToken.getAccessableType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid invoice link", "This invoice access token is invalid.");
        }
        if (accessToken.getExpiry() != null && accessToken.getExpiry().isBefore(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.GONE, "Invoice link expired", "This invoice access token has expired.");
        }
        UUID invoiceId;
        try {
            invoiceId = UUID.fromString(accessToken.getAccessableId());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid invoice link", "This invoice access token is invalid.");
        }
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found", "The invoice for this access token no longer exists."));
        return invoice;
    }

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public Optional<AccessToken> findLatestActiveTokenForInvoice(UUID invoiceId) {
        return accessTokenRepository.findFirstByTypeAndAccessableTypeAndAccessableIdOrderByCreatedAtDesc(
                INVOICE_ACCESS_TOKEN_TYPE,
                INVOICE_ACCESSABLE_TYPE,
                invoiceId.toString()
        ).filter(token -> token.getExpiry() == null || !token.getExpiry().isBefore(OffsetDateTime.now()));
    }
}
