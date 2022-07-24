/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.controller.api.v2.model;

import alfio.controller.api.support.BookingInfoTicket;
import alfio.model.BillingDetails;
import alfio.model.OrderSummary;
import alfio.model.SummaryRow.SummaryType;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation.TicketReservationStatus;
import alfio.model.subscription.UsageDetails;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @param formattedExpirationDate          map of language -> formatted date
 * @param addCompanyBillingDetails         billing info from additional info
 * @param containsCategoriesLinkedToGroups reservation info group related info
 */
public record ReservationInfo(String id,
                              String shortId,
                              String firstName,
                              String lastName,
                              String email,
                              long validity,
                              List<TicketsByTicketCategory> ticketsByCategory,
                              alfio.controller.api.v2.model.ReservationInfo.ReservationInfoOrderSummary orderSummary,
                              TicketReservationStatus status,
                              boolean validatedBookingInformation,
                              Map<String, String> formattedExpirationDate,
                              String invoiceNumber,
                              boolean invoiceRequested,
                              boolean invoiceOrReceiptDocumentPresent,
                              boolean paid,
                              boolean tokenAcquired,
                              PaymentProxy paymentProxy,
                              Boolean addCompanyBillingDetails,
                              String customerReference,
                              Boolean skipVatNr,
                              String billingAddress,
                              BillingDetails billingDetails,
                              boolean containsCategoriesLinkedToGroups,
                              Map<PaymentMethod, PaymentProxyWithParameters> activePaymentMethods,
                              List<SubscriptionInfo> subscriptionInfos) {
    //


    public record TicketsByTicketCategory(String name,
                                          TicketCategory.TicketAccessType ticketAccessType,
                                          List<BookingInfoTicket> tickets) {
    }

    @Getter
    public static class ReservationInfoOrderSummary {

        private final List<ReservationInfoOrderSummaryRow> summary;
        private final String totalPrice;
        private final boolean free;
        private final boolean displayVat;
        private final int priceInCents;
        private final String descriptionForPayment;
        private final String totalVAT;
        private final String vatPercentage;
        private final boolean notYetPaid;

        public ReservationInfoOrderSummary(OrderSummary orderSummary) {
            this.summary = orderSummary.getSummary()
                .stream()
                .map(s -> new ReservationInfoOrderSummaryRow(s.getName(), s.getAmount(), s.getPrice(), s.getSubTotal(), s.getType(), s.getTaxPercentage()))
                .collect(Collectors.toList());
            this.totalPrice = orderSummary.getTotalPrice();
            this.free = orderSummary.getFree();
            this.displayVat = orderSummary.getDisplayVat();
            this.priceInCents = orderSummary.getPriceInCents();
            this.descriptionForPayment = orderSummary.getDescriptionForPayment();
            this.totalVAT = orderSummary.getTotalVAT();
            this.vatPercentage = orderSummary.getVatPercentage();
            this.notYetPaid = orderSummary.getNotYetPaid();
        }
    }


    public record ReservationInfoOrderSummaryRow(String name,
                                                 int amount, String price,
                                                 String subTotal,
                                                 SummaryType type,
                                                 String taxPercentage) {
    }

    public record SubscriptionInfo(UUID id, String pin, UsageDetails usageDetails, SubscriptionOwner owner) {
    }

    public record SubscriptionOwner(String firstName, String lastName, String email) {
    }

}
