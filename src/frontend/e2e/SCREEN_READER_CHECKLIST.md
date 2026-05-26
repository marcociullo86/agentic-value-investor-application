# Screen Reader Accessibility Checklist

> Manual verification checklist for WCAG 2.2 AA screen reader compliance.
> TSK-193 · US-072 · EP-016
>
> Test with: VoiceOver (macOS), NVDA (Windows), or TalkBack (Android).

## Headings (SC 1.3.1 Info and Relationships)

- [ ] Each page has exactly one `<h1>` announcing the page purpose
- [ ] Heading levels follow sequential order (h1 → h2 → h3, no skips)
- [ ] Screen reader heading navigation (VO+Cmd+H / NVDA+H) lists all headings correctly
- [ ] Login page: h1 reads "Accedi"
- [ ] Register page: h1 reads "Registrati"
- [ ] Analysis page: h1 announces ticker/company name
- [ ] Top picks page: h1 reads "Top Value Picks"
- [ ] Watchlist page: h1 reads "Watchlist"

## Form Labels (SC 1.3.1, 4.1.2 Name/Role/Value)

- [ ] Login form: "Email" label announced on email input focus
- [ ] Login form: "Password" label announced on password input focus
- [ ] Register form: all fields announce their labels on focus
- [ ] Watchlist add ticker: input announces its label/purpose
- [ ] Search bar: announces "Cerca ticker o nome azienda" on focus
- [ ] Form error messages are announced via `aria-describedby` linkage
- [ ] Form error summary (role="alert") is announced immediately on validation failure

## Analysis Results (SC 1.3.1, 4.1.3 Status Messages)

- [ ] Traffic light panel: rule signal cards announce rule name + signal status
- [ ] Expanding a traffic light card announces the revealed details
- [ ] Valuation summary announces key metrics (intrinsic value, margin of safety)
- [ ] DCF override panel: inputs announce their labels
- [ ] Stale data badge (role="alert"): announced immediately when visible

## Notifications / Toast (SC 4.1.3 Status Messages)

- [ ] Toast notifications use `aria-live="polite"` region
- [ ] Error toasts use `role="alert"` (assertive)
- [ ] Success notifications are announced without interrupting current task
- [ ] Notification dismiss button is keyboard accessible and labeled

## Tables (SC 1.3.1 Info and Relationships)

- [ ] Top picks table: column headers (`<th>`) are announced per cell
- [ ] Watchlist table: headers announced correctly
- [ ] Table navigation (VoiceOver: Ctrl+Opt+arrows) works as expected
- [ ] Empty state message announced when table has no rows

## Navigation (SC 2.4.1 Bypass Blocks, 2.4.2 Page Titled)

- [ ] Each page has a descriptive `<title>` (announced on page load)
- [ ] Navbar links announce their destination
- [ ] Current page indicator is conveyed (aria-current="page")
- [ ] Skip-to-content link present and functional (if implemented)

## Focus Management (SC 2.4.3 Focus Order)

- [ ] After login redirect: focus moves to meaningful content
- [ ] After search submission: focus moves to results area
- [ ] After adding watchlist item: focus returns to input or announces success
- [ ] After removing watchlist item: focus moves to logical next element
- [ ] Modal/dialog: focus trapped inside, Escape closes and returns focus

## Deep Analysis Page

- [ ] Verdict badge announces verdict text with proper context
- [ ] Drawdown chart has accessible description or is marked decorative
- [ ] Edgar filing links announce document type and date
- [ ] Munger report collapsible section announces expanded/collapsed state
- [ ] News sentiment chips announce sentiment value

---

## Testing Protocol

1. Enable VoiceOver (Cmd+F5 on macOS) or NVDA (Ctrl+Alt+N on Windows)
2. Navigate each page using only screen reader commands
3. Verify each checkbox above
4. Document any failures with: page, element, expected vs actual announcement
5. File issues for failures as new TSKs referencing US-072

## Results

| Date | Tester | SR Used | Pass | Fail | Notes |
|------|--------|---------|------|------|-------|
| _pending_ | _TBD_ | _TBD_ | — | — | Initial audit pending |
