package dev.jordi.senda.space;

import dev.jordi.senda.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/spaces")
public class SpaceController {

    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceResponse create(@Valid @RequestBody CreateSpaceRequest request) {
        return spaceService.create(CurrentUser.id(), request);
    }

    @GetMapping
    public List<SpaceResponse> list() {
        return spaceService.listMine(CurrentUser.id());
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceMemberResponse addMember(@PathVariable Long id,
                                         @Valid @RequestBody AddMemberRequest request) {
        return spaceService.addMember(CurrentUser.id(), id, request);
    }

    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable Long id) {
        spaceService.accept(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable Long id) {
        spaceService.decline(CurrentUser.id(), id);
    }

    @DeleteMapping("/{id}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long id) {
        spaceService.leave(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/members")
    public List<SpaceMemberResponse> members(@PathVariable Long id) {
        return spaceService.listMembers(CurrentUser.id(), id);
    }
}
