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
package alfio.model.group;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;

public record LinkedGroup(@Column("id") int id,
                          @Column("a_group_id_fk") int groupId,
                          @Column("event_id_fk") Integer eventId,
                          @Column("ticket_category_id_fk") Integer ticketCategoryId,
                          @Column("type") Type type,
                          @Column("match_type") MatchType matchType,
                          @Column("max_allocation") Integer maxAllocation) {


    /**
     * Link type
     */
    public enum Type {
        /**
         * Allow exactly one ticket per group member
         */
        ONCE_PER_VALUE,
        /**
         * Allow a limited quantity ( 1 < n < ∞)
         */
        LIMITED_QUANTITY,
        /**
         * No limit
         */
        UNLIMITED
    }

    public enum MatchType {
        /**
         * The given email address *must* match the group member's email address exactly
         */
        FULL,
        /**
         * Try to find a FULL match; if not successful, try to match email domain (everything after '@')
         */
        EMAIL_DOMAIN
    }
}
