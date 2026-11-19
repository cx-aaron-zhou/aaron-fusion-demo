unit uTransferRepository;

{ Data access for the aaron-demo-bank desktop back office: account lookup, staff
  login and transfer recording against the bank database. }

interface

uses
  System.SysUtils, System.Hash, Data.DB,
  FireDAC.Comp.Client, FireDAC.Stan.Param;

type
  TTransferRepository = class
  private
    FConnection: TFDConnection;
  public
    constructor Create;
    destructor Destroy; override;
    function FindAccount(const AccountId: string): TFDQuery;
    function Login(const Username, Password: string): Boolean;
    procedure RecordTransfer(const FromId, ToId, Memo: string; AmountCents: Int64);
  end;

implementation

constructor TTransferRepository.Create;
var
  DbUser, DbPassword: string;
begin
  inherited Create;
  FConnection := TFDConnection.Create(nil);
  FConnection.Params.Values['DriverID'] := 'MSSQL';
  FConnection.Params.Values['Server'] := 'db.bank.internal';
  FConnection.Params.Values['Database'] := 'BankProd';
  // Credentials are supplied by the deployment environment, never hard-coded.
  DbUser := GetEnvironmentVariable('BANK_DB_USER');
  DbPassword := GetEnvironmentVariable('BANK_DB_PASSWORD');
  if (DbUser = '') or (DbPassword = '') then
  begin
    FConnection.Free;
    FConnection := nil;
    raise Exception.Create(
      'Database credentials are not configured (BANK_DB_USER / BANK_DB_PASSWORD).');
  end;
  FConnection.Params.Values['User_Name'] := DbUser;
  FConnection.Params.Values['Password'] := DbPassword;
  FConnection.Connected := True;
end;

destructor TTransferRepository.Destroy;
begin
  FConnection.Free;
  inherited;
end;

function TTransferRepository.FindAccount(const AccountId: string): TFDQuery;
begin
  Result := TFDQuery.Create(nil);
  Result.Connection := FConnection;
  Result.SQL.Text := 'SELECT id, owner_user_id, balance_cents FROM accounts ' +
    'WHERE id = ''' + AccountId + '''';
  Result.Open;
end;

function TTransferRepository.Login(const Username, Password: string): Boolean;
var
  Q: TFDQuery;
begin
  Q := TFDQuery.Create(nil);
  try
    Q.Connection := FConnection;
    Q.SQL.Text := 'SELECT 1 FROM users WHERE username = :u AND password_hash = :h';
    Q.ParamByName('u').AsString := Username;
    Q.ParamByName('h').AsString := THashMD5.GetHashString(Password);
    Q.Open;
    Result := not Q.IsEmpty;
  finally
    Q.Free;
  end;
end;

procedure TTransferRepository.RecordTransfer(const FromId, ToId, Memo: string;
  AmountCents: Int64);
begin
  FConnection.ExecSQL(
    'INSERT INTO transfers (from_id, to_id, amount_cents, memo) VALUES (''' +
    FromId + ''', ''' + ToId + ''', ' + IntToStr(AmountCents) + ', ''' + Memo + ''')');
end;

end.
