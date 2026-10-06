package com.educationerp.importer;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The seven things the blueprint lists as importable, and the columns each one needs.
 *
 * <p>The fields are declared here rather than inside each importer so that the upload screen can
 * describe what a spreadsheet must contain before anybody has found one to upload. A person
 * preparing a file should be able to ask the system what it wants, not guess and be refused.
 *
 * <p>Aliases carry the words a school is likely to have written in the header. "Date of birth",
 * "DOB", "dob" and "birth_date" all mean the same column, and insisting on an exact match would
 * send people through the mapping screen for no reason.
 */
@Component
public class ImportDefinitions {

    /** The columns each record type needs, in the order they should be asked for. */
    public ImportField[] fields(ImportBatch.ImportType type) {
        return switch (type) {
            case STUDENTS -> students();
            case GUARDIANS -> guardians();
            case EMPLOYEES -> employees();
            case COURSES -> courses();
            case FEES -> fees();
            case INVENTORY -> inventory();
            case BOOKS -> books();
        };
    }

    /** The permission that lets somebody confirm an import of this type. */
    public String confirmPermission(ImportBatch.ImportType type) {
        return switch (type) {
            case STUDENTS -> "STUDENT_CREATE";
            case GUARDIANS -> "STUDENT_UPDATE";
            case EMPLOYEES -> "EMPLOYEE_CREATE";
            case COURSES -> "ACADEMIC_CREATE";
            case FEES -> "FEE_CREATE";
            case INVENTORY -> "INVENTORY_MANAGE";
            case BOOKS -> "LIBRARY_MANAGE";
        };
    }

    /** The permission that lets somebody see and validate an import. */
    public String viewPermission(ImportBatch.ImportType type) {
        return switch (type) {
            case STUDENTS -> "STUDENT_READ";
            case GUARDIANS -> "STUDENT_READ";
            case EMPLOYEES -> "EMPLOYEE_READ";
            case COURSES -> "ACADEMIC_READ";
            case FEES -> "FEE_READ";
            case INVENTORY -> "INVENTORY_READ";
            case BOOKS -> "LIBRARY_READ";
        };
    }

    /** A worked example, for the template a school can download and fill in. */
    public List<String> templateHeaders(ImportBatch.ImportType type) {
        return java.util.Arrays.stream(fields(type)).map(ImportField::label).toList();
    }

    private ImportField[] students() {
        return new ImportField[]{
                ImportField.text("firstName", "First Name", true, 100, "first name", "given name"),
                ImportField.text("lastName", "Last Name", true, 100, "last name", "surname",
                        "family name"),
                ImportField.text("middleName", "Middle Name", false, 100, "middle name"),
                ImportField.date("dateOfBirth", "Date of Birth", false, "dob", "birth date",
                        "birthdate"),
                ImportField.choice("gender", "Gender", false,
                        List.of("MALE", "FEMALE", "OTHER"), "sex"),
                ImportField.date("enrollmentDate", "Enrollment Date", false, "admission date",
                        "joining date"),
                ImportField.text("nationality", "Nationality", false, 80),
                ImportField.text("phone", "Phone", false, 60, "mobile", "contact number",
                        "phone number"),
                ImportField.email("email", "Email", "e-mail", "email address"),
                ImportField.text("address", "Address", false, 400)};
    }

    private ImportField[] guardians() {
        return new ImportField[]{
                ImportField.text("firstName", "First Name", true, 100, "first name"),
                ImportField.text("lastName", "Last Name", true, 100, "last name", "surname"),
                ImportField.text("phone", "Phone", true, 60, "mobile", "contact number"),
                ImportField.email("email", "Email", "e-mail"),
                ImportField.text("occupation", "Occupation", false, 120, "job"),
                ImportField.text("address", "Address", false, 400)};
    }

    private ImportField[] employees() {
        return new ImportField[]{
                ImportField.text("firstName", "First Name", true, 100, "first name"),
                ImportField.text("lastName", "Last Name", true, 100, "last name", "surname"),
                ImportField.text("middleName", "Middle Name", false, 100),
                ImportField.date("dateOfBirth", "Date of Birth", false, "dob"),
                ImportField.choice("gender", "Gender", false,
                        List.of("MALE", "FEMALE", "OTHER"), "sex"),
                ImportField.text("phone", "Phone", false, 60, "mobile"),
                ImportField.email("email", "Email", "e-mail"),
                ImportField.date("joinDate", "Join Date", true, "joining date", "start date",
                        "date of joining"),
                ImportField.text("departmentCode", "Department Code", false, 40,
                        "department", "department name"),
                ImportField.text("designationCode", "Designation Code", false, 40,
                        "designation", "job title"),
                ImportField.text("nationality", "Nationality", false, 80)};
    }

    private ImportField[] courses() {
        return new ImportField[]{
                ImportField.text("code", "Code", true, 40, "course code", "subject code"),
                ImportField.text("name", "Name", true, 200, "course name", "subject",
                        "subject name"),
                ImportField.text("description", "Description", false, 500),
                ImportField.number("creditHours", "Credit Hours", false, "credits"),
                ImportField.number("totalMarks", "Total Marks", false, "marks"),
                ImportField.text("departmentCode", "Department Code", false, 40, "department")};
    }

    private ImportField[] fees() {
        return new ImportField[]{
                ImportField.text("structureName", "Structure Name", true, 150,
                        "fee structure", "name"),
                ImportField.text("structureCode", "Structure Code", true, 40, "code"),
                ImportField.text("componentName", "Component Name", true, 150, "component",
                        "component name"),
                ImportField.number("amount", "Amount", true, "fee", "value"),
                ImportField.text("currency", "Currency", false, 10),
                ImportField.number("installments", "Installments", false, "installments count",
                        "number of installments")};
    }

    private ImportField[] inventory() {
        return new ImportField[]{
                ImportField.text("code", "Code", true, 40, "item code"),
                ImportField.text("name", "Name", true, 200, "item name", "item"),
                ImportField.text("categoryCode", "Category Code", false, 40, "category"),
                ImportField.text("unit", "Unit", false, 20),
                ImportField.number("reorderLevel", "Reorder Level", false, "minimum level"),
                ImportField.number("reorderQuantity", "Reorder Quantity", false, "reorder qty"),
                ImportField.text("description", "Description", false, 500)};
    }

    private ImportField[] books() {
        return new ImportField[]{
                ImportField.text("title", "Title", true, 400, "book title", "name"),
                ImportField.text("isbn", "ISBN", false, 40),
                ImportField.text("publisher", "Publisher", false, 200),
                ImportField.text("author", "Author", false, 200),
                ImportField.number("publicationYear", "Publication Year", false, "year",
                        "published"),
                ImportField.text("language", "Language", false, 40),
                ImportField.text("category", "Category", false, 150),
                ImportField.text("callNumber", "Call Number", false, 60)};
    }
}