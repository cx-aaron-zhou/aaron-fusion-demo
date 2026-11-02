program BankBackOffice;

{$APPTYPE CONSOLE}

uses
  System.SysUtils, FireDAC.Comp.Client,
  uTransferRepository in 'uTransferRepository.pas';

var
  Repo: TTransferRepository;
  Accounts: TFDQuery;
begin
  if ParamCount < 3 then
  begin
    Writeln('usage: BankBackOffice <staff> <password> <accountId>');
    Halt(2);
  end;
  Repo := TTransferRepository.Create;
  try
    if not Repo.Login(ParamStr(1), ParamStr(2)) then
    begin
      Writeln('login failed');
      Halt(1);
    end;
    Accounts := Repo.FindAccount(ParamStr(3));
    try
      while not Accounts.Eof do
      begin
        Writeln(Accounts.FieldByName('id').AsString, ' ',
          Accounts.FieldByName('balance_cents').AsString);
        Accounts.Next;
      end;
    finally
      Accounts.Free;
    end;
  finally
    Repo.Free;
  end;
end.
