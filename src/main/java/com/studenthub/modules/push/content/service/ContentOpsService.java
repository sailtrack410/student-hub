package com.studenthub.modules.push.content.service;

import com.studenthub.modules.push.content.controller.dto.ContentCalendarRequest;
import com.studenthub.modules.push.content.controller.dto.ContentCalendarResponse;
import com.studenthub.modules.push.content.controller.dto.ContentPreviewResponse;
import com.studenthub.modules.push.content.controller.dto.ContentSlotRequest;
import com.studenthub.modules.push.content.controller.dto.ContentSlotResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容运营（A03/C09）：运营位与内容日历的 CRUD、上下线、预览。
 */
public interface ContentOpsService {

    List<ContentSlotResponse> listSlots(String slotType, String status, String position, String keyword);

    ContentSlotResponse createSlot(ContentSlotRequest request);

    ContentSlotResponse updateSlot(Long id, ContentSlotRequest request);

    void deleteSlot(Long id);

    ContentSlotResponse onlineSlot(Long id);

    ContentSlotResponse offlineSlot(Long id);

    ContentPreviewResponse previewSlot(Long id);

    List<ContentCalendarResponse> listCalendar(LocalDateTime from, LocalDateTime to, String status, String entryType);

    ContentCalendarResponse createCalendar(ContentCalendarRequest request);

    ContentCalendarResponse updateCalendar(Long id, ContentCalendarRequest request);

    void deleteCalendar(Long id);

    ContentCalendarResponse publishCalendar(Long id);

    ContentCalendarResponse offlineCalendar(Long id);

    ContentPreviewResponse previewCalendar(Long id);
}
