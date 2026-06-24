package dev.jordi.senda.networth;

import dev.jordi.senda.common.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/networth")
public class NetWorthController {

    private final NetWorthService netWorthService;

    public NetWorthController(NetWorthService netWorthService) {
        this.netWorthService = netWorthService;
    }

    @GetMapping
    public NetWorthResponse get() {
        return netWorthService.calculate(CurrentUser.id());
    }

    @GetMapping("/history")
    public List<NetWorthHistoryPoint> history(@RequestParam(defaultValue = "30") int days) {
        return netWorthService.history(CurrentUser.id(), days);
    }
}
