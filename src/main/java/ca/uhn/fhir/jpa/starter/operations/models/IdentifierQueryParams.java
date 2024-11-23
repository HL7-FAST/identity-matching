package ca.uhn.fhir.jpa.starter.operations.models;


public class IdentifierQueryParams {

	private String _identifierSystem;
	private String _identifierValue;
	private String _identifierTypeCode;
	private String _identifierTypeSystem;

	public IdentifierQueryParams() {}

	public IdentifierQueryParams(String system, String value, String typeCode, String typeSystem) {
		_identifierSystem = system;
		_identifierValue = value;
		_identifierTypeCode = typeCode;
		_identifierTypeSystem = typeSystem;
	}

	public String getIdentifierSystem() { return _identifierSystem; }
	public void setIdentifierSystem(String system) { this._identifierSystem = system; }

	public String getIdentifierValue() { return _identifierValue; }
	public void setIdentifierValue(String value) { this._identifierValue = value; }

	public String getIdentifierTypeCode() { return _identifierTypeCode; }
	public void setIdentifierTypeCode(String code) { this._identifierTypeCode = code; }

	public String getIdentifierTypeSystem() { return _identifierTypeSystem; }
	public void setIdentifierTypeSystem(String system) { this._identifierTypeSystem = system; }

}
