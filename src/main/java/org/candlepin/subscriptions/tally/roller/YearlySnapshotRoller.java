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
package org.candlepin.subscriptions.tally.roller;

import static org.candlepin.subscriptions.db.model.Granularity.YEARLY;

import java.util.Collection;
import java.util.List;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.tally.AccountUsageCalculation;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces yearly snapshots based on data stored in the inventory service. If a snapshot does not
 * exist for the account for the current year and an incoming calculation exists for the account, a
 * new snapshot will be created. A snapshot's cores, sockets, and instances will only be updated if
 * the incoming calculated values are greater than those existing for the current year.
 */
public class YearlySnapshotRoller extends BaseSnapshotRoller {

  private static final Logger log = LoggerFactory.getLogger(YearlySnapshotRoller.class);

  public YearlySnapshotRoller(
      TallySnapshotRepository tallyRepo, ApplicationClock clock, TagProfile tagProfile) {
    super(tallyRepo, clock, tagProfile);
  }

  @Override
  @Transactional
  public Collection<TallySnapshot> rollSnapshots(AccountUsageCalculation accountCalc) {
    var orgId = accountCalc.getOrgId();
    log.debug("Producing yearly snapshots for orgId={}.", orgId);

    List<TallySnapshot> currentYearlySnaps =
        getCurrentSnapshotsByOrgId(
            orgId,
            getApplicableProducts(accountCalc, YEARLY),
            YEARLY,
            clock.startOfCurrentYear(),
            clock.endOfCurrentYear());

    return updateSnapshots(accountCalc, currentYearlySnaps, YEARLY);
  }
}
