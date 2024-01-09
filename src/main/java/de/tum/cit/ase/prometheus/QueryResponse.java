package de.tum.cit.ase.prometheus;

public class QueryResponse {

    private String status;
    private Data data;

    public QueryResponse() {}

    public String getStatus() {
        return status;
    }

    public Data getData() {
        return data;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public static class Data {

        private Result[] result;

        public Data() {}

        public Result[] getResult() {
            return result;
        }

        public void setResult(Result[] result) {
            this.result = result;
        }
    }

    public static class Result {

        private Object[][] values;

        public Result() {}

        public Object[][] getValues() {
            return values;
        }

        public void setValues(Object[][] values) {
            this.values = values;
        }
    }
}
