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
package alfio.job.executor;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.manager.*;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.UploadBase64FileModification;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static alfio.test.util.IntegrationTestUtil.*;
import static alfio.test.util.IntegrationTestUtil.confirmAndLinkSubscription;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
class AssignTicketToSubscriberJobExecutorIntegrationTest {

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");
    private static final String FIRST_CATEGORY_NAME = "default";

    private final EventManager eventManager;
    private final UserManager userManager;
    private final SubscriptionManager subscriptionManager;
    private final SubscriptionRepository subscriptionRepository;
    private final FileUploadManager fileUploadManager;
    private final ConfigurationRepository configurationRepository;
    private final OrganizationRepository organizationRepository;
    private final EventRepository eventRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final AssignTicketToSubscriberJobExecutor executor;
    private final AdminReservationRequestRepository adminReservationRequestRepository;
    private final AdminReservationRequestManager adminReservationRequestManager;
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final NotificationManager notificationManager;

    private Event event;
    private String userId;

    @Autowired
    AssignTicketToSubscriberJobExecutorIntegrationTest(EventManager eventManager,
                                                       UserManager userManager,
                                                       SubscriptionManager subscriptionManager,
                                                       SubscriptionRepository subscriptionRepository,
                                                       FileUploadManager fileUploadManager,
                                                       ConfigurationRepository configurationRepository,
                                                       OrganizationRepository organizationRepository,
                                                       EventRepository eventRepository,
                                                       TicketReservationRepository ticketReservationRepository,
                                                       AssignTicketToSubscriberJobExecutor executor,
                                                       AdminReservationRequestRepository adminReservationRequestRepository,
                                                       AdminReservationRequestManager adminReservationRequestManager,
                                                       UserRepository userRepository,
                                                       AuthorityRepository authorityRepository,
                                                       NamedParameterJdbcTemplate jdbcTemplate,
                                                       TicketRepository ticketRepository,
                                                       TicketCategoryRepository ticketCategoryRepository,
                                                       NotificationManager notificationManager) {
        this.eventManager = eventManager;
        this.userManager = userManager;
        this.subscriptionManager = subscriptionManager;
        this.subscriptionRepository = subscriptionRepository;
        this.fileUploadManager = fileUploadManager;
        this.configurationRepository = configurationRepository;
        this.organizationRepository = organizationRepository;
        this.eventRepository = eventRepository;
        this.ticketReservationRepository = ticketReservationRepository;
        this.executor = executor;
        this.adminReservationRequestRepository = adminReservationRequestRepository;
        this.adminReservationRequestManager = adminReservationRequestManager;
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.ticketRepository = ticketRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.notificationManager = notificationManager;
    }

    @BeforeEach
    void setUp() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, FIRST_CATEGORY_NAME, TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "hidden", TicketCategory.TicketAccessType.INHERIT, 2,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.ONE, true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        var uploadFileForm = new UploadBase64FileModification();
        uploadFileForm.setFile(BaseIntegrationTest.ONE_PIXEL_BLACK_GIF);
        uploadFileForm.setName("my-image.gif");
        uploadFileForm.setType("image/gif");
        String fileBlobId = fileUploadManager.insertFile(uploadFileForm);
        assertNotNull(fileBlobId);
        this.event = eventAndUser.getLeft();
        this.userId = eventAndUser.getRight();
        // init admin user
        userRepository.create(UserManager.ADMIN_USERNAME, "", "The", "Administrator", "admin@localhost", true, User.Type.INTERNAL, null, null);
        authorityRepository.create(UserManager.ADMIN_USERNAME, Role.ADMIN.getRoleName());
    }

    @AfterEach
    void tearDown() {
        try {
            eventManager.deleteEvent(event.getId(), userId);
        } catch(Exception ex) {
            //ignore exception because the transaction might be aborted
        }
    }

    @Test
    void process() {
        int maxEntries = 2;
        var descriptorId = createSubscriptionDescriptor(event.getOrganizationId(), fileUploadManager, subscriptionManager, maxEntries);
        var subscriptionIdAndPin = confirmAndLinkSubscription(descriptorId, event.getOrganizationId(), subscriptionRepository, ticketReservationRepository, maxEntries);
        subscriptionRepository.linkSubscriptionAndEvent(descriptorId, event.getId(), 0, event.getOrganizationId());
        assertEquals(1, subscriptionRepository.loadAvailableSubscriptionsByEvent().size());
        // trigger job schedule with flag not active
        executor.process(null);
        assertEquals(1, subscriptionRepository.loadAvailableSubscriptionsByEvent().size());
        assertEquals(0, adminReservationRequestRepository.countPending());

        // try again with flag active
        configurationRepository.insert(ConfigurationKeys.GENERATE_TICKETS_FOR_SUBSCRIPTIONS.name(), "true", "");
        executor.process(null);
        assertEquals(1, subscriptionRepository.loadAvailableSubscriptionsByEvent().size());
        assertEquals(1, adminReservationRequestRepository.countPending());

        // trigger reservation processing
        var result = adminReservationRequestManager.processPendingReservations();
        assertEquals(1, result.getLeft()); //  1 success
        assertEquals(0, result.getRight()); // 0 failures
        assertEquals(0, subscriptionRepository.loadAvailableSubscriptionsByEvent().size());

        // check ticket
        var ticketUuid = jdbcTemplate.queryForObject("select uuid from ticket where event_id = :eventId and ext_reference = :ref",
            Map.of("eventId", event.getId(), "ref", subscriptionIdAndPin.getLeft() + "_auto"),
            String.class);
        assertNotNull(ticketUuid);

        // check category
        var ticket = ticketRepository.findByUUID(ticketUuid);
        assertEquals(Ticket.TicketStatus.ACQUIRED, ticket.getStatus());
        var category = ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId());
        assertTrue(category.isPresent());
        assertEquals(FIRST_CATEGORY_NAME, category.get().getName());

        // check reservation
        var reservation = ticketReservationRepository.findReservationById(ticket.getTicketsReservationId());
        assertEquals(TicketReservation.TicketReservationStatus.COMPLETE, reservation.getStatus());
        assertEquals(PaymentProxy.ADMIN, reservation.getPaymentMethod());
        assertEquals(BigDecimal.ZERO, reservation.getFinalPrice());

        // trigger email send
        int sent = notificationManager.sendWaitingMessages();
        assertTrue(sent > 0);
        var messagesPair = notificationManager.loadAllMessagesForPurchaseContext(event, null, null);
        assertEquals(1, messagesPair.getLeft());
        assertTrue(messagesPair.getRight().stream().allMatch(m -> m.getStatus() == EmailMessage.Status.SENT));

    }
}