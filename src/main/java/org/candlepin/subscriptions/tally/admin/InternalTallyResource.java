/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.tally.admin;

import java.time.OffsetDateTime;
import javax.ws.rs.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.retention.TallyRetentionController;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.candlepin.subscriptions.tally.MarketplaceResendTallyController;
import org.candlepin.subscriptions.tally.TallySnapshotController;
import org.candlepin.subscriptions.tally.admin.api.InternalApi;
import org.candlepin.subscriptions.tally.admin.api.model.DefaultResponse;
import org.candlepin.subscriptions.tally.admin.api.model.EventsResponse;
import org.candlepin.subscriptions.tally.admin.api.model.OptInResponse;
import org.candlepin.subscriptions.tally.admin.api.model.TallyResend;
import org.candlepin.subscriptions.tally.admin.api.model.TallyResendData;
import org.candlepin.subscriptions.tally.admin.api.model.TallyResponse;
import org.candlepin.subscriptions.tally.admin.api.model.UuidList;
import org.candlepin.subscriptions.tally.billing.RemittanceController;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.DateRange;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** This resource is for exposing administrator REST endpoints for Tally. */
@Component
@Slf4j
public class InternalTallyResource implements InternalApi {

  private final ApplicationClock clock;
  private final ApplicationProperties applicationProperties;
  private final MarketplaceResendTallyController resendTallyController;
  private final RemittanceController remittanceController;
  private final TallySnapshotController tallySnapshotController;
  private final CaptureSnapshotsTaskManager snapshotsTaskManager;
  private final TallyRetentionController retentionController;
  private final InternalTallyDataController internalTallyDataController;
  private final SecurityProperties properties;

  public static final String FEATURE_NOT_ENABLED_MESSSAGE =
      "This feature is not currently enabled.";
  private static final String SUCCESS_STATUS = "Success";
  private static final String REJECTED_STATUS = "Rejected";

  @SuppressWarnings("java:S107")
  public InternalTallyResource(
      ApplicationClock clock,
      ApplicationProperties applicationProperties,
      MarketplaceResendTallyController resendTallyController,
      RemittanceController remittanceController,
      TallySnapshotController tallySnapshotController,
      CaptureSnapshotsTaskManager snapshotsTaskManager,
      TallyRetentionController retentionController,
      InternalTallyDataController internalTallyDataController,
      SecurityProperties properties) {
    this.clock = clock;
    this.applicationProperties = applicationProperties;
    this.resendTallyController = resendTallyController;
    this.remittanceController = remittanceController;
    this.tallySnapshotController = tallySnapshotController;
    this.snapshotsTaskManager = snapshotsTaskManager;
    this.retentionController = retentionController;
    this.internalTallyDataController = internalTallyDataController;
    this.properties = properties;
  }

  @Override
  public void performHourlyTallyForOrg(
      String orgId, OffsetDateTime start, OffsetDateTime end, Boolean xRhSwatchSynchronousRequest) {
    DateRange range = new DateRange(start, end);
    if (!clock.isHourlyRange(range)) {
      throw new IllegalArgumentException(
          String.format(
              "Start/End times must be at the top of the hour: [%s -> %s]",
              range.getStartString(), range.getEndString()));
    }

    if (ResourceUtils.sanitizeBoolean(xRhSwatchSynchronousRequest, false)) {
      if (!applicationProperties.isEnableSynchronousOperations()) {
        throw new BadRequestException("Synchronous tally operations are not enabled.");
      }
      log.info("Synchronous hourly tally requested for orgId {}: {}", orgId, range);
      tallySnapshotController.produceHourlySnapshotsForOrg(orgId, range);
    } else {
      snapshotsTaskManager.tallyOrgByHourly(orgId, range);
    }
  }

  @Override
  public TallyResend resendTally(UuidList uuidList) {
    var tallies = resendTallyController.resendTallySnapshots(uuidList.getUuids());
    return new TallyResend().data(new TallyResendData().talliesResent(tallies));
  }

  @Override
  public void syncRemittance() {
    remittanceController.syncRemittance();
  }

  @Override
  public DefaultResponse purgeTallySnapshots() {
    try {
      log.info("Initiating tally snapshot purge.");
      retentionController.purgeSnapshotsAsync();
    } catch (TaskRejectedException e) {
      log.warn("A tally snapshots purge job is already running.");
      return getDefaultResponse(REJECTED_STATUS);
    }
    return getDefaultResponse(SUCCESS_STATUS);
  }

  /**
   * Clear tallies, hosts, and events for a given org ID. Enabled via ENABLE_ACCOUNT_RESET
   * environment variable. Intended only for non-prod environments.
   *
   * @param orgId Red Hat orgId
   */
  @Override
  public TallyResponse deleteDataAssociatedWithOrg(String orgId) {
    var response = new TallyResponse();
    if (isFeatureEnabled()) {
      log.info("Received request to delete all data associated with orgId {}", orgId);
      try {
        internalTallyDataController.deleteDataAssociatedWithOrg(orgId);
      } catch (Exception e) {
        log.error("Unable to delete data for organization {} due to {}", orgId, e);
        response.setDetail(String.format("Unable to delete data for organization %s", orgId));
        return response;
      }
      var successMessage = "Finished deleting data associated with organization " + orgId;
      response.setDetail(successMessage);
      log.info(successMessage);
      return response;
    } else {
      response.setDetail(FEATURE_NOT_ENABLED_MESSSAGE);
      return response;
    }
  }

  /**
   * Delete an event. Supported only in dev-mode.
   *
   * @param eventId Event Id
   * @return success or error message
   */
  @Override
  public EventsResponse deleteEvent(String eventId) {
    var response = new EventsResponse();
    if (isFeatureEnabled()) {
      try {
        internalTallyDataController.deleteEvent(eventId);
        response.setDetail(String.format("Successfully deleted Event with ID: %s", eventId));
        return response;
      } catch (Exception e) {
        log.error("Failed to delete Event with ID: {}  Cause: {}", eventId, e.getMessage());
        response.setDetail(String.format("Failed to delete Event with ID: %s ", eventId));
        return response;
      }
    } else {
      response.setDetail(FEATURE_NOT_ENABLED_MESSSAGE);
      return response;
    }
  }

  /**
   * Fetch events by org
   *
   * @param orgId Red Hat orgId
   * @param begin Beginning of time range (inclusive)
   * @param end End of time range (exclusive)
   * @return success or error message
   */
  @Override
  public EventsResponse fetchEventsForOrgIdInTimeRange(
      String orgId, OffsetDateTime begin, OffsetDateTime end) {
    var response = new EventsResponse();
    try {
      response.setDetail(
          internalTallyDataController.fetchEventsForOrgIdInTimeRange(orgId, begin, end));
      return response;
    } catch (Exception e) {
      log.error("Unable to deserialize event list ", e);
      response.setDetail("Unable to deserialize event list");
      return response;
    }
  }

  /**
   * Save a list of events. Supported only in dev-mode.
   *
   * @param jsonListOfEvents Event list specified as JSON
   * @return success or error message
   */
  @Override
  public EventsResponse saveEvents(String jsonListOfEvents) {
    var response = new EventsResponse();
    if (isFeatureEnabled()) {
      response.setDetail(internalTallyDataController.saveEvents(jsonListOfEvents));
      return response;

    } else {
      response.setDetail(FEATURE_NOT_ENABLED_MESSSAGE);
      return response;
    }
  }

  /** Update tally snapshots for all orgs */
  @Override
  public DefaultResponse tallyConfiguredOrgs() {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Tally for all orgs triggered over API by {}", principal);
    internalTallyDataController.tallyConfiguredOrgs();
    return getDefaultResponse(SUCCESS_STATUS);
  }

  /** Trigger a tally for an org */
  @Override
  public DefaultResponse tallyOrg(String orgId) {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Tally for org {} triggered over API by {}", orgId, principal);
    internalTallyDataController.tallyOrg(orgId);
    return getDefaultResponse(SUCCESS_STATUS);
  }

  /**
   * Trigger hourly tally for all configured orgs for the specified range. The 'start' and 'end'
   * parameters MUST be specified as a pair to complete a range. If they are left empty, a date
   * range is used based on NOW with the configured offsets applied (identical to if the job was
   * run)
   *
   * @param start
   * @param end
   * @throws IllegalArgumentException
   */
  @Override
  public DefaultResponse tallyAllOrgsByHourly(String start, String end)
      throws IllegalArgumentException {

    DateRange range = null;
    if (StringUtils.hasText(start) || StringUtils.hasText(end)) {
      try {
        range = DateRange.fromStrings(start, end);
        log.info(
            "Hourly tally for all orgs triggered for range {} over API by {}",
            range,
            ResourceUtils.getPrincipal());
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "Both startDateTime and endDateTime must be set to " + "valid date Strings.");
      }
    } else {
      log.info(
          "Hourly tally for all accounts triggered over API by {}", ResourceUtils.getPrincipal());
    }

    internalTallyDataController.tallyAllOrgsByHourly(range);
    return getDefaultResponse(SUCCESS_STATUS);
  }

  /**
   * Create or update an opt in configuration. This operation is idempotent
   *
   * @param accountNumber
   * @param orgId
   * @return success or error message
   */
  @Override
  public OptInResponse createOrUpdateOptInConfig(String accountNumber, String orgId) {
    var response = new OptInResponse();
    Object principal = ResourceUtils.getPrincipal();
    log.info(
        "Opt in for account {}, org {} triggered via API by {}", accountNumber, orgId, principal);
    log.debug("Creating OptInConfig over API for account {}, org {}", accountNumber, orgId);
    response.setDetail(
        internalTallyDataController.createOrUpdateOptInConfig(accountNumber, orgId, OptInType.API));
    return response;
  }

  private boolean isFeatureEnabled() {
    if (!properties.isDevMode() && !properties.isManualEventEditingEnabled()) {
      log.error(FEATURE_NOT_ENABLED_MESSSAGE);
      return false;
    }
    return true;
  }

  @NotNull
  private DefaultResponse getDefaultResponse(String status) {
    var response = new DefaultResponse();
    response.setStatus(status);
    return response;
  }
}
