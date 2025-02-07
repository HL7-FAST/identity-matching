# FAST Identity Matching Reference Implementation

This is a FHIR server reference implementation of the [FAST Interoperable Digital Identity and Patient Matching IG](https://build.fhir.org/ig/HL7/fhir-identity-matching-ig/).  It is built on the [HAPI FHIR JPA Starter Project](https://github.com/hapifhir/hapi-fhir-jpaserver-starter) project and more detailed configuration information can be found in that repository.

## Foundry
A live demo is hosted by [HL7 FHIR Foundry](https://foundry.hl7.org/products/7dee5dfd-d098-433c-9377-c15faa5c878b), where you may also download curated configurations to run yourself.

## Prerequisites
Building and running the server locally requires either Docker or
- Java 17+
- Maven

## Using Maven

```bash
mvn spring-boot:run
```
or
```bash
mvn -Pjetty spring-boot:run
```

## Using Docker

```bash
docker compose up -d
```

## `$match` and `$idi-match` Operations

The server contains patient matching operations as described in the IG in the [Patient Matching](https://build.fhir.org/ig/HL7/fhir-identity-matching-ig/patient-matching.html) section.  These are reachable via a POST to the `[host]/fhir/Patient/$match` or `[host]/fhir/Patient/$idi-match` operation endpoints.

### Patient Parameter Validation

The server has the ability to validate an incoming patient parameter for its match operations against the three Patient profiles from the IG:
- [IDI Patient](https://build.fhir.org/ig/HL7/fhir-identity-matching-ig/StructureDefinition-IDI-Patient.html)
- [IDI Patient L0](https://build.fhir.org/ig/HL7/fhir-identity-matching-ig/StructureDefinition-IDI-Patient-L0.html)
- [IDI Patient L1](https://build.fhir.org/ig/HL7/fhir-identity-matching-ig/StructureDefinition-IDI-Patient-L1.html)

The level of required validation can be configured via the `hapi.fhir.match-validation-level` property or by supplying a valid validation level value in a request header.  The name of this header can be configured via the `hapi.fhir.match-validation-header` property and defaults to `X-Match-Validation`.  For example, to disable validation for a request, you can supply `X-Match-Validation: NONE` in the request.

The validation level can be set to one of the following values:

| Value | Description |
| --- | --- |
| `DEFAULT` | Requires that the Patient validates against an IDI-Patient profile specified in the meta.profile field. If no profile is provided, the Patient will be validated against the [base IDI-Patient profile](http://hl7.org/fhir/us/identity-matching/StructureDefinition/IDI-Patient) |
| `META_PROFILE` | Validate the Patient resource against the most restrictive IDI-Patient profile specified in the `meta.profile` field. If an expected IDI-Patient profile is not found, the validation will fail. |
| `NONE` | No validation is performed |

If the validation fails, the server will return a `400 Bad Request` response with an `OperationOutcome`.


## Security

The server supports requiring an auth token for incoming requests and is integrated with the [UDAP Reference Implementation](https://github.com/HL7-FAST/udap) which implements the [FAST Security IG](https://build.fhir.org/ig/HL7/fhir-udap-security-ig/).

Security is toggled via the `security.enable-authentication` property.  When enabled, a valid UDAP server is required to be set via the `security.issuer` property. This is set by default to the [UDAP RI hosted in Foundry](https://udap-security.fast.hl7.org).

A valid certificate is also required.  This can be set via the `security.cert-file` and `security.cert-password` properties.  Additionally, the server has the ability to use the UDAP RI's certificate generation endpoint to generate a certificate.  This can be toggled via the `security.fetch-cert` property.  Using this method will create a certificate file named `generated-cert.pfx` in the server's working directory.  This is intended for local testing.

Security can also be disabled by supplying a header in the request.  The name of this header is configured in the `security.bypass-header` property and defaults to `X-Allow-Public-Access`.  No value is required for this header.

## Remote Server Matching

> [!WARNING]
> This feature is experimental.

The server supports the ability to perform a match operation against remote FHIR servers.  This is enabled by supplying a special header in the `POST` to the match operation (`$match` or `$idi-match`).

The name of this header is configured in the `hapi.fhir.remote-match-header` property and defaults to `X-Remote-Match`.  The behavior of this header is as follows:

| Value | cURL Header Example | Behavior |
| --- | --- | -- |
| Blank | `-H 'X-Remote-Match'` | The server will perform a match operation against the remote server(s) specified in the `hapi.fhir.remote-servers` property. This property should be set to a list of base FHIR endpoints. |
| Comma-separated list | `-H 'X-Remote-Match: http://localhost:8081/fhir,https://hapi.fhir.org/baseR4'` | The server will perform a match operation against each of the remote servers in the list. |
| No header | | No remote matching will be performed even if the `hapi.fhir.remote-servers` property is set. |



## Questions and Contributions
Questions about the project can be asked in the [FAST Identity stream on the FHIR Zulip Chat](https://chat.fhir.org/#narrow/stream/294750-FHIR-at-Scale-Taskforce-.28FAST.29.3A-Identity).

This project welcomes Pull Requests. Any issues identified with the RI should be submitted via the [GitHub issue tracker](https://github.com/HL7-FAST/identity-matching/issues).

As of October 1, 2022, The Lantana Consulting Group is responsible for the management and maintenance of this Reference Implementation.
In addition to posting on FHIR Zulip Chat channel mentioned above you can contact [Corey Spears](mailto:corey.spears@lantanagroup.com) for questions or requests.
