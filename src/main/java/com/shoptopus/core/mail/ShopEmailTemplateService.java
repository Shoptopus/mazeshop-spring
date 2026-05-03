package com.shoptopus.core.mail;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ShopEmailTemplateService {

    private final EmailSettingsService emailSettingsService;

    public ShopEmailTemplateService(EmailSettingsService emailSettingsService) {
        this.emailSettingsService = emailSettingsService;
    }

    public String wrap(String title, String preheader, String bodyHtml) {
        String fromName = emailSettingsService.readSettings().fromName();
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f4efe8;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#231a13;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4efe8;padding:24px 0;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;background:#fffaf4;border:1px solid rgba(35,26,19,0.12);border-radius:24px;overflow:hidden;">
                          <tr>
                            <td style="padding:28px 32px;background:linear-gradient(135deg,#231a13,#624336);color:#fffaf4;">
                              <div style="font-size:12px;letter-spacing:0.18em;text-transform:uppercase;opacity:0.74;">%s</div>
                              <h1 style="margin:12px 0 0;font-size:28px;line-height:1.2;">%s</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px;">
                              %s
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(title),
                escape(preheader == null ? "" : preheader),
                escape(fromName),
                escape(title),
                bodyHtml
        );
    }

    public String paragraph(String value) {
        return "<p style=\"margin:0 0 16px;line-height:1.6;\">%s</p>".formatted(value);
    }

    public String cta(String label, String url) {
        return """
                <p style="margin:24px 0 0;">
                  <a href="%s" style="display:inline-block;padding:12px 18px;border-radius:999px;background:linear-gradient(135deg,#231a13,#624336);color:#fffaf4;text-decoration:none;font-weight:600;">%s</a>
                </p>
                """.formatted(escapeAttribute(url), escape(label));
    }

    public String note(String value) {
        return "<p style=\"margin:16px 0 0;color:#6f635b;font-size:14px;line-height:1.5;\">%s</p>".formatted(value);
    }

    public String definitionList(Map<String, String> values) {
        StringBuilder html = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;margin:16px 0;\">");
        values.forEach((label, value) -> html.append("""
                <tr>
                  <td style="padding:8px 0;color:#6f635b;width:42%%;vertical-align:top;">%s</td>
                  <td style="padding:8px 0;font-weight:600;vertical-align:top;">%s</td>
                </tr>
                """.formatted(escape(label), escape(value == null ? "—" : value))));
        html.append("</table>");
        return html.toString();
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapeAttribute(String value) {
        return escape(value == null ? "" : value);
    }
}
