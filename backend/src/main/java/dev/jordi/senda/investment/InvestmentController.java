package dev.jordi.senda.investment;

import dev.jordi.senda.common.CurrentUser;
import dev.jordi.senda.common.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    private final InvestmentService investmentService;

    public InvestmentController(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    // --- Asset classes ---

    @GetMapping("/asset-classes")
    public List<AssetClassResponse> listAssetClasses() {
        return investmentService.listAssetClasses(CurrentUser.id());
    }

    @PostMapping("/asset-classes")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetClassResponse createAssetClass(@Valid @RequestBody AssetClassRequest request) {
        return investmentService.createAssetClass(CurrentUser.id(), request);
    }

    @PutMapping("/asset-classes/{id}")
    public AssetClassResponse updateAssetClass(@PathVariable Long id,
                                               @Valid @RequestBody AssetClassRequest request) {
        return investmentService.updateAssetClass(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/asset-classes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssetClass(@PathVariable Long id) {
        investmentService.deleteAssetClass(CurrentUser.id(), id);
    }

    // --- Holdings ---

    @GetMapping("/holdings")
    public List<HoldingResponse> listHoldings(@RequestParam(required = false) Long assetClassId) {
        return investmentService.listHoldings(CurrentUser.id(), assetClassId);
    }

    @PostMapping("/holdings")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldingResponse createHolding(@Valid @RequestBody HoldingRequest request) {
        return investmentService.createHolding(CurrentUser.id(), request);
    }

    @GetMapping("/holdings/{id}")
    public HoldingResponse getHolding(@PathVariable Long id) {
        return investmentService.getHolding(CurrentUser.id(), id);
    }

    @PostMapping("/holdings/{id}/buys")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldingResponse addBuy(@PathVariable Long id, @Valid @RequestBody BuyRequest request) {
        return investmentService.addBuy(CurrentUser.id(), id, request);
    }

    @GetMapping("/holdings/{id}/lots")
    public List<HoldingLotResponse> listLots(@PathVariable Long id) {
        return investmentService.listLots(CurrentUser.id(), id);
    }

    @PutMapping("/holdings/{id}/price")
    public HoldingResponse setPrice(@PathVariable Long id, @Valid @RequestBody PriceRequest request) {
        return investmentService.setPrice(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/holdings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHolding(@PathVariable Long id) {
        investmentService.deleteHolding(CurrentUser.id(), id);
    }

    @PostMapping("/refresh-prices")
    public List<HoldingResponse> refreshPrices() {
        return investmentService.refreshPrices(CurrentUser.id());
    }

    // --- NFTs ---

    @GetMapping("/nfts")
    public List<NftResponse> listNfts() {
        return investmentService.listNfts(CurrentUser.id());
    }

    @PostMapping("/nfts")
    @ResponseStatus(HttpStatus.CREATED)
    public NftResponse createNft(@Valid @RequestBody NftRequest request) {
        return investmentService.createNft(CurrentUser.id(), request);
    }

    @PutMapping("/nfts/{id}")
    public NftResponse updateNft(@PathVariable Long id, @Valid @RequestBody NftRequest request) {
        return investmentService.updateNft(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/nfts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNft(@PathVariable Long id) {
        investmentService.deleteNft(CurrentUser.id(), id);
    }

    /**
     * Controller-local handler: takes precedence over the global advice so
     * business-rule violations of this module map to 400 with a clear message.
     */
    @ExceptionHandler(InvalidInvestmentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInvestment(InvalidInvestmentException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request", ex.getMessage()));
    }
}
