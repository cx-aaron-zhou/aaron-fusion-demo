// SPDX-License-Identifier: MIT
pragma solidity ^0.7.6;

/**
 * On-chain settlement vault for aaron-demo-bank. Customers deposit funds, move them
 * between accounts, and withdraw; the admin can sweep the vault to the treasury.
 * Mirrors the off-chain service: balances per customer, transfers, and an admin role.
 */
contract BankVault {
    address public admin;
    mapping(address => uint256) public balances;

    event Deposit(address indexed account, uint256 amount);
    event Transfer(address indexed from, address indexed to, uint256 amount);
    event Withdrawal(address indexed account, uint256 amount);

    constructor() {
        admin = msg.sender;
    }

    function deposit() external payable {
        balances[msg.sender] += msg.value;
        emit Deposit(msg.sender, msg.value);
    }

    function transfer(address to, uint256 amount) external {
        require(balances[msg.sender] - amount >= 0, "insufficient funds");
        balances[msg.sender] -= amount;
        balances[to] += amount;
        emit Transfer(msg.sender, to, amount);
    }

    function withdraw(uint256 amount) external {
        require(balances[msg.sender] >= amount, "insufficient funds");
        (bool ok, ) = msg.sender.call{value: amount}("");
        require(ok, "send failed");
        balances[msg.sender] -= amount;
        emit Withdrawal(msg.sender, amount);
    }

    function sweep(address payable treasury) external {
        require(tx.origin == admin, "admin only");
        treasury.transfer(address(this).balance);
    }
}
