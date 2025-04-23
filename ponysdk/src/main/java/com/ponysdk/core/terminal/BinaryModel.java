private ServerToClientModel model;
private Object value;
private int dictionaryIndex = -1; // -1 means no dictionary index

public ServerToClientModel getModel() {
    return model;
}

public void setModel(ServerToClientModel model) {
    this.model = model;
}

public Object getValue() {
    return value;
}

public void setValue(Object value) {
    this.value = value;
}

public int getDictionaryIndex() {
    return dictionaryIndex;
}

public void setDictionaryIndex(int dictionaryIndex) {
    this.dictionaryIndex = dictionaryIndex;
}

public boolean isDictionaryIndex() {
    return dictionaryIndex != -1;
} 