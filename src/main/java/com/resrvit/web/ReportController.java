package com.resrvit.web;

import com.resrvit.service.ReportService;
import com.resrvit.service.TimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reports;
    private final TimeService time;

    @GetMapping
    public String page(@RequestParam(defaultValue = "UTILISATION") ReportService.Kind kind,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, Model model) {
        LocalDate t = to != null ? to : time.today();
        LocalDate f = from != null ? from : t.minusDays(29);
        model.addAttribute("kind", kind);
        model.addAttribute("kinds", ReportService.Kind.values());
        model.addAttribute("from", f);
        model.addAttribute("to", t);
        model.addAttribute("table", reports.build(kind, f, t));
        return "admin/reports";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam ReportService.Kind kind, @RequestParam String format,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportService.Table table = reports.build(kind, from, to);
        boolean pdf = "pdf".equalsIgnoreCase(format);
        String name = "resrvit-" + kind.name().toLowerCase().replace('_', '-') + "-" + from + "-to-" + to + (pdf ? ".pdf" : ".csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(pdf ? MediaType.APPLICATION_PDF : new MediaType("text", "csv"))
                .body(pdf ? reports.pdf(table) : reports.csv(table));
    }
}
