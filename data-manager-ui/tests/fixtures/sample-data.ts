export const sampleTableMetadata = {
  id: 1,
  label: 'Test Table',
  physicalName: 'test_table',
  description: 'Test description',
  versionNo: 1,
  createdAt: '2024-01-01T00:00:00Z',
  createdBy: 'test_user',
  updatedAt: '2024-01-01T00:00:00Z',
  updatedBy: 'test_user',
};

export const sampleColumnMetadata = [
  {
    id: 1,
    tableId: 1,
    label: 'Name',
    physicalName: 'name',
    tablePhysicalName: 'test_table',
    type: 'VARCHAR',
    versionNo: 1,
    createdAt: '2024-01-01T00:00:00Z',
    createdBy: 'test_user',
    updatedAt: '2024-01-01T00:00:00Z',
    updatedBy: 'test_user',
  },
  {
    id: 2,
    tableId: 1,
    label: 'Age',
    physicalName: 'age',
    tablePhysicalName: 'test_table',
    type: 'INTEGER',
    versionNo: 1,
    createdAt: '2024-01-01T00:00:00Z',
    createdBy: 'test_user',
    updatedAt: '2024-01-01T00:00:00Z',
    updatedBy: 'test_user',
  },
];

export const sampleRowData = [
  { id: 1, name: 'John', age: 30 },
  { id: 2, name: 'Jane', age: 25 },
  { id: 3, name: 'Bob', age: 40 },
];

export const sampleCsvContent = `name,age,city
John,30,NYC
Jane,25,LA
Bob,40,Chicago`;

export const largeCsvContent = Array.from({ length: 1000 }, (_, i) => 
  `name${i},age${i},city${i}`
).join('\n');

