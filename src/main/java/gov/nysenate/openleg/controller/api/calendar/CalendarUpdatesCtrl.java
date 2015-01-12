package gov.nysenate.openleg.controller.api.calendar;

import com.google.common.collect.Range;
import gov.nysenate.openleg.client.response.base.BaseResponse;
import gov.nysenate.openleg.client.response.base.ListViewResponse;
import gov.nysenate.openleg.client.view.calendar.CalendarIdView;
import gov.nysenate.openleg.client.view.updates.UpdateDigestView;
import gov.nysenate.openleg.client.view.updates.UpdateTokenView;
import gov.nysenate.openleg.controller.api.base.BaseCtrl;
import gov.nysenate.openleg.dao.base.LimitOffset;
import gov.nysenate.openleg.dao.base.PaginatedList;
import gov.nysenate.openleg.dao.base.SortOrder;
import gov.nysenate.openleg.dao.calendar.data.CalendarUpdatesDao;
import gov.nysenate.openleg.model.calendar.CalendarId;
import gov.nysenate.openleg.model.updates.UpdateDigest;
import gov.nysenate.openleg.model.updates.UpdateToken;
import gov.nysenate.openleg.model.updates.UpdateType;
import gov.nysenate.openleg.service.calendar.data.CalendarDataService;
import gov.nysenate.openleg.util.DateUtils;
import org.apache.lucene.search.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import static gov.nysenate.openleg.controller.api.base.BaseCtrl.BASE_API_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = BASE_API_PATH + "/calendars", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
public class CalendarUpdatesCtrl extends BaseCtrl {

    private static final Logger logger = LoggerFactory.getLogger(CalendarUpdatesCtrl.class);

    @Autowired protected CalendarUpdatesDao calendarUpdatesDao;
    @Autowired protected CalendarDataService calendarDataService;

    /**
     * Calendar updates API
     *
     * Get calendar updates:    (GET) /api/3/calendars/updates/{from}/{to}
     *    (from date to now)    (GET) /api/3/calendars/updates/{from}
     *         (past 7 days)    (GET) /api/3/calendars/updates/
     *
     * Where 'from' and 'to' are ISO date times.
     *
     * Request Parameters:      detail (boolean) - Returns detailed update responses if set to true
     *                          type (string) - Update type (processed, published) Default: published
     *                          limit, offset (int) - Paginate
     *                          order (string) - Order by update date
     *
     */
    @RequestMapping(value = "/updates/{from}/{to}")
    public BaseResponse getUpdatesDuring(@PathVariable String from, @PathVariable String to,
                                         WebRequest webRequest) {
        LocalDateTime fromDateTime = parseISODateTime(from, "from");
        LocalDateTime toDateTime = parseISODateTime(to, "to");
        boolean detail = getBooleanParam(webRequest, "detail", false);
        UpdateType updateType = getUpdateTypeFromParam(webRequest);
        Range<LocalDateTime> updateRange = Range.openClosed(fromDateTime, toDateTime);
        SortOrder dateOrder = getSortOrder(webRequest, SortOrder.DESC);
        LimitOffset limitOffset = getLimitOffset(webRequest, 100);
        BaseResponse response = null;
        if (detail) {
            PaginatedList<UpdateDigest<CalendarId>> updateTokenDigests =
                    calendarUpdatesDao.getUpdateDigests(updateType, updateRange, dateOrder, limitOffset);
            response = ListViewResponse.of(
                    updateTokenDigests.getResults().stream()
                            .map(ud -> new UpdateDigestView(ud, new CalendarIdView(ud.getId())))
                            .collect(Collectors.toList()),
                    updateTokenDigests.getTotal(), updateTokenDigests.getLimOff()
            );
        }
        else {
            PaginatedList<UpdateToken<CalendarId>> updateTokens =
                calendarUpdatesDao.calendarsUpdatedDuring(updateType, updateRange, dateOrder, limitOffset);
            response =  ListViewResponse.of(
                updateTokens.getResults().stream()
                    .map(token ->  new UpdateTokenView(token, new CalendarIdView(token.getId())))
                    .collect(Collectors.toList()),
                updateTokens.getTotal(), updateTokens.getLimOff()
            );
        }
        return response;
    }

    @RequestMapping(value = "/updates/{from}")
    public BaseResponse getUpdatesDuring(@PathVariable String from,
                                         WebRequest webRequest) {
        return getUpdatesDuring(from, LocalDateTime.now().toString(), webRequest);
    }

    @RequestMapping(value = "/updates")
    public BaseResponse getUpdatesDuring(WebRequest webRequest) {
        return getUpdatesDuring(LocalDateTime.now().minusDays(7).toString(), LocalDateTime.now().toString(), webRequest);
    }

    /**
     * Updates for calendar API
     *
     * Get updates for specific calendar:   (GET) /api/3/calendars/{year}/{calendarNo}/updates/{from}/{to}
     *                  (get all updates)   (GET) /api/3/calendars/{year}/{calendarNo}/updates/
     *
     * Where 'from' and 'to' are ISO date times.
     *
     * Request parameters:      order - The sort order of the update response (orderd by published date) (default DESC)
     *                          limit - The maximum number of updates to return (default 100)
     *                          offset - Return updates starting with offset (default 1)
     */
    @RequestMapping(value = "/{year:[\\d]{4}}/{calendarNo:\\d+}/updates/{from}/{to}")
    public BaseResponse getUpdatesForCalendarDuring(@PathVariable int year, @PathVariable int calendarNo,
                                                    @PathVariable String from, @PathVariable String to,
                                                    WebRequest webRequest) {
        LocalDateTime fromDateTime = parseISODateTime(from, "from");
        LocalDateTime toDateTime = parseISODateTime(to, "to");
        UpdateType updateType = getUpdateTypeFromParam(webRequest);
        SortOrder dateOrder = getSortOrder(webRequest, SortOrder.DESC);
        LimitOffset limitOffset = getLimitOffset(webRequest, 100);
        PaginatedList<UpdateDigest<CalendarId>> updateDigests =
                calendarUpdatesDao.getUpdateDigests(updateType, new CalendarId(calendarNo, year),
                                                    Range.openClosed(fromDateTime, toDateTime), dateOrder, limitOffset);
        return ListViewResponse.of(
                updateDigests.getResults().stream()
                        .map(digest -> new UpdateDigestView(digest, new CalendarIdView(digest.getId())))
                        .collect(Collectors.toList()),
                updateDigests.getTotal(), LimitOffset.ALL
        );
    }

    @RequestMapping(value = "/{year:[\\d]{4}}/{calendarNo:\\d+}/updates")
    public BaseResponse getUpdatesForCalendar(@PathVariable int year, @PathVariable int calendarNo,
                                              WebRequest webRequest) {
        return getUpdatesForCalendarDuring(year, calendarNo,
                DateUtils.LONG_AGO.atStartOfDay().toString(), DateUtils.THE_FUTURE.atStartOfDay().toString(), webRequest);
    }
}
