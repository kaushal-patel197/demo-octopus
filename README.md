# InvoiceWorks

Meridian Business Systems — InvoiceWorks v2.4.1. Small-business invoicing.
In service since 2009.

Build: `mvn package` · Run: `java -jar target/invoiceworks.jar`

Totals work the way you'd expect: we add up the line items, take off any
discount the customer has earned, then apply provincial tax to what's left.
Example: $100 of items, 10% loyalty discount, 13% tax → $90 + $11.70 = $101.70.

Loyalty customers get 5% off. Alberta is GST-only.

*Fictional legacy codebase generated for a conference demo. Not a real product.*
Licensed under the MIT License — see LICENSE.
